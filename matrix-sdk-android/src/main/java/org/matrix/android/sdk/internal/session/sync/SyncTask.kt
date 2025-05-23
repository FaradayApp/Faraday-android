/*
 * Copyright 2020 The Matrix.org Foundation C.I.C.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.matrix.android.sdk.internal.session.sync

import android.os.SystemClock
import okhttp3.ResponseBody
import org.matrix.android.sdk.api.auth.AuthenticationService
import org.matrix.android.sdk.api.extensions.tryOrNull
import org.matrix.android.sdk.api.logger.LoggerTag
import org.matrix.android.sdk.api.session.Session
import org.matrix.android.sdk.api.session.statistics.StatisticEvent
import org.matrix.android.sdk.api.session.sync.InitialSyncStep
import org.matrix.android.sdk.api.session.sync.InitialSyncStrategy
import org.matrix.android.sdk.api.session.sync.SyncRequestState
import org.matrix.android.sdk.api.session.sync.initialSyncStrategy
import org.matrix.android.sdk.api.session.sync.model.LazyRoomSyncEphemeral
import org.matrix.android.sdk.api.session.sync.model.SyncResponse
import org.matrix.android.sdk.internal.di.SessionFilesDirectory
import org.matrix.android.sdk.internal.di.UserId
import org.matrix.android.sdk.internal.network.GlobalErrorReceiver
import org.matrix.android.sdk.internal.network.MultiServerCredentials
import org.matrix.android.sdk.internal.network.TimeOutInterceptor
import org.matrix.android.sdk.internal.network.executeRequest
import org.matrix.android.sdk.internal.network.toFailure
import org.matrix.android.sdk.internal.session.SessionListeners
import org.matrix.android.sdk.internal.session.dispatchTo
import org.matrix.android.sdk.internal.session.filter.GetCurrentFilterTask
import org.matrix.android.sdk.internal.session.homeserver.GetHomeServerCapabilitiesTask
import org.matrix.android.sdk.internal.session.profile.LocalAccount
import org.matrix.android.sdk.internal.session.sync.parsing.InitialSyncResponseParser
import org.matrix.android.sdk.internal.session.user.UserStore
import org.matrix.android.sdk.internal.task.Task
import org.matrix.android.sdk.internal.util.logDuration
import org.matrix.android.sdk.internal.util.time.Clock
import retrofit2.Response
import retrofit2.awaitResponse
import timber.log.Timber
import java.io.File
import java.net.SocketTimeoutException
import javax.inject.Inject

private val loggerTag = LoggerTag("SyncTask", LoggerTag.SYNC)

internal interface SyncTask : Task<SyncTask.Params, SyncResponse> {

    data class Params(
            val timeout: Long,
            val presence: SyncPresence?,
            val afterPause: Boolean
    )
}

internal fun SyncResponse.getUnreadCount(): Int {
    var unreadCount = 0
    rooms?.let {
        it.join.values.forEach { room ->
            room.unreadNotifications?.notificationCount?.let { unreadCount += it }
        }
        it.leave.values.forEach { room ->
            room.unreadNotifications?.notificationCount?.let { unreadCount += it }
        }
    }
    return unreadCount
}

internal class DefaultSyncTask @Inject constructor(
        private val syncAPI: MultiServerSyncApi,
        private val authenticationService: AuthenticationService, // TODO: add user to params and make sequential task execution for each account
        @UserId private val userId: String,
        private val syncResponseHandler: SyncResponseHandler,
        private val syncRequestStateTracker: SyncRequestStateTracker,
        private val syncTokenStore: SyncTokenStore,
        private val userStore: UserStore,
        private val session: Session,
        private val sessionListeners: SessionListeners,
        private val syncTaskSequencer: SyncTaskSequencer,
        private val globalErrorReceiver: GlobalErrorReceiver,
        @SessionFilesDirectory
        private val fileDirectory: File,
        private val syncResponseParser: InitialSyncResponseParser,
        private val roomSyncEphemeralTemporaryStore: RoomSyncEphemeralTemporaryStore,
        private val clock: Clock,
        private val getHomeServerCapabilitiesTask: GetHomeServerCapabilitiesTask,
        private val getCurrentFilterTask: GetCurrentFilterTask
) : SyncTask {

    private val workingDir = File(fileDirectory, "is")
    private val initialSyncStatusRepository: InitialSyncStatusRepository = FileInitialSyncStatusRepository(workingDir, clock)
    private val accountStore = authenticationService.getLocalAccountStore()

    override suspend fun execute(params: SyncTask.Params): SyncResponse {
        return syncTaskSequencer.post {
            val accounts = accountStore.getAccounts()
            var mainAccount: LocalAccount? = null
            accounts.forEach { account ->
                if (account.userId != userId) {
                    val response = doSync(account.userId, MultiServerCredentials(account.token, account.homeServerUrl), params, accountUnreadCount = account.unreadCount)
                    Timber.d("sync response multiserver: $response")
                    val unreadCount = response.getUnreadCount()

                    if (account.unreadCount != unreadCount) {
                        Timber.d("updating unread count")
                        accountStore.updateUnreadCount(account.userId, unreadCount)
                    }
                } else {
                    mainAccount = account
                }
            }
            doSync(mainAccount!!.userId, MultiServerCredentials(mainAccount!!.token, mainAccount!!.homeServerUrl), params, isMainAccount = true)
        }
    }

    private suspend fun doSync(
            userId: String,
            credentials: MultiServerCredentials,
            params: SyncTask.Params,
            isMainAccount: Boolean = false,
            accountUnreadCount: Int? = null
    ): SyncResponse {
        Timber.tag(loggerTag.value).i("Sync task started on Thread: ${Thread.currentThread().name}")

        val requestParams = HashMap<String, String>()
        var timeout = 0L
        val token = syncTokenStore.getLastToken(userId)
        if (token != null) {
            requestParams["since"] = token
            timeout = params.timeout
        }

        // Maybe refresh the homeserver capabilities data we know
        getHomeServerCapabilitiesTask.execute(GetHomeServerCapabilitiesTask.Params(forceRefresh = false))
        val filter = getCurrentFilterTask.execute(Unit)
        val readTimeOut = (params.timeout + TIMEOUT_MARGIN).coerceAtLeast(TimeOutInterceptor.DEFAULT_LONG_TIMEOUT)

        requestParams["timeout"] = timeout.toString()
        requestParams["filter"] = filter
        params.presence?.let { requestParams["set_presence"] = it.value }

        if (!isMainAccount) {
            return syncAPI.sync(
                    requestCredentials = credentials,
                    params = hashMapOf(
                            "timeout" to (6000L).toString(),
                            "set_presence" to SyncPresence.Online.value
                    ).apply { token?.let { set("since", it) } },
                    readTimeOut = readTimeOut
            ).also {
                Timber.d("updating need test: ${it.getUnreadCount()} - $accountUnreadCount")
                if (it.getUnreadCount() != accountUnreadCount)
                    syncResponseHandler.handleResponse(userId, it, null, afterPause = true, null)
            }
        }
        val isInitialSync = token == null
        if (isInitialSync) {
            // We might want to get the user information in parallel too
            val user = tryOrNull { session.profileService().getProfileAsUser(userId) }
            userStore.createOrUpdate(
                    userId = userId,
                    displayName = user?.displayName,
                    avatarUrl = user?.avatarUrl
            )
            syncRequestStateTracker.startRoot(InitialSyncStep.ImportingAccount, 100)
        }

        var syncResponseToReturn: SyncResponse? = null
        val syncStatisticsData = SyncStatisticsData(isInitialSync, params.afterPause)
        if (isInitialSync) {
            Timber.tag(loggerTag.value).i("INIT_SYNC with filter: ${requestParams["filter"]}")
            val initSyncStrategy = initialSyncStrategy
            logDuration("INIT_SYNC strategy: $initSyncStrategy", loggerTag, clock) {
                if (initSyncStrategy is InitialSyncStrategy.Optimized) {
                    roomSyncEphemeralTemporaryStore.reset()
                    workingDir.mkdirs()
                    val file = downloadInitSyncResponse(credentials, requestParams, syncStatisticsData)
                    syncResponseToReturn = reportSubtask(syncRequestStateTracker, InitialSyncStep.ImportingAccount, 1, 0.7F) {
                        handleSyncFile(file, initSyncStrategy)
                    }
                    // Delete all files
                    workingDir.deleteRecursively()
                } else {
                    val syncResponse = logDuration("INIT_SYNC Request", loggerTag, clock) {
                        executeRequest(globalErrorReceiver) {
                            syncAPI.sync(
                                    requestCredentials = credentials,
                                    params = requestParams,
                                    readTimeOut = readTimeOut,
                            )
                        }
                    }
                    // We cannot distinguish request and download in this case.
                    syncStatisticsData.requestInitSyncTime = SystemClock.elapsedRealtime()
                    syncStatisticsData.downloadInitSyncTime = syncStatisticsData.requestInitSyncTime
                    logDuration("INIT_SYNC Database insertion", loggerTag, clock) {
                        syncResponseHandler.handleResponse(userId, syncResponse, null, afterPause = true, syncRequestStateTracker)
                    }
                    syncResponseToReturn = syncResponse
                }
            }
            syncRequestStateTracker.endAll()
        } else {
            Timber.tag(loggerTag.value).i("Start incremental sync request with since token $token")
            syncRequestStateTracker.setSyncRequestState(SyncRequestState.IncrementalSyncIdle)
            val syncResponse = try {
                executeRequest(globalErrorReceiver) {
                    syncAPI.sync(
                            requestCredentials = credentials,
                            params = requestParams,
                            readTimeOut = readTimeOut
                    )
                }
            } catch (throwable: Throwable) {
                Timber.tag(loggerTag.value).e(throwable, "Incremental sync request error")
                syncRequestStateTracker.setSyncRequestState(SyncRequestState.IncrementalSyncError)
                throw throwable
            }
            val nbRooms = syncResponse.rooms?.invite.orEmpty().size + syncResponse.rooms?.join.orEmpty().size + syncResponse.rooms?.leave.orEmpty().size
            val nbToDevice = syncResponse.toDevice?.events.orEmpty().size
            val nextBatch = syncResponse.nextBatch
            Timber.tag(loggerTag.value).i(
                    "Incremental sync request parsing, $nbRooms room(s) $nbToDevice toDevice(s). Got nextBatch: $nextBatch"
            )
            syncRequestStateTracker.setSyncRequestState(
                    SyncRequestState.IncrementalSyncParsing(
                            rooms = nbRooms,
                            toDevice = nbToDevice,
                    )
            )
            syncResponseHandler.handleResponse(userId, syncResponse, token, afterPause = params.afterPause, null)
            syncResponseToReturn = syncResponse
            Timber.tag(loggerTag.value).i("Incremental sync done")
            syncRequestStateTracker.setSyncRequestState(SyncRequestState.IncrementalSyncDone)
        }
        syncStatisticsData.treatmentSyncTime = SystemClock.elapsedRealtime()
        syncStatisticsData.nbOfRooms = syncResponseToReturn?.rooms?.join?.size ?: 0
        sendStatistics(syncStatisticsData)
        Timber.tag(loggerTag.value).i("Sync task finished on Thread: ${Thread.currentThread().name}")
        // Should throw if null as it's a mandatory value.
        return syncResponseToReturn!!
    }

    private suspend fun downloadInitSyncResponse(credentials: MultiServerCredentials, requestParams: Map<String, String>, syncStatisticsData: SyncStatisticsData): File {
        val workingFile = File(workingDir, "initSync.json")
        val status = initialSyncStatusRepository.getStep()
        if (workingFile.exists() && status >= InitialSyncStatus.STEP_DOWNLOADED) {
            Timber.tag(loggerTag.value).i("INIT_SYNC file is already here")
            reportSubtask(syncRequestStateTracker, InitialSyncStep.Downloading, 1, 0.3f) {
                // Empty task
            }
        } else {
            initialSyncStatusRepository.setStep(InitialSyncStatus.STEP_DOWNLOADING)
            val syncResponse = logDuration("INIT_SYNC Perform server request", loggerTag, clock) {
                reportSubtask(syncRequestStateTracker, InitialSyncStep.ServerComputing, 1, 0.2f) {
                    getSyncResponse(credentials, requestParams, MAX_NUMBER_OF_RETRY_AFTER_TIMEOUT)
                }
            }
            syncStatisticsData.requestInitSyncTime = SystemClock.elapsedRealtime()
            if (syncResponse.isSuccessful) {
                logDuration("INIT_SYNC Download and save to file", loggerTag, clock) {
                    reportSubtask(syncRequestStateTracker, InitialSyncStep.Downloading, 1, 0.1f) {
                        syncResponse.body()?.byteStream()?.use { inputStream ->
                            workingFile.outputStream().use { outputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        }
                    }
                }
            } else {
                throw syncResponse.toFailure(globalErrorReceiver)
                        .also { Timber.tag(loggerTag.value).w("INIT_SYNC request failure: $this") }
            }
            syncStatisticsData.downloadInitSyncTime = SystemClock.elapsedRealtime()
            initialSyncStatusRepository.setStep(InitialSyncStatus.STEP_DOWNLOADED)
        }
        return workingFile
    }

    private suspend fun getSyncResponse(credentials: MultiServerCredentials, requestParams: Map<String, String>, maxNumberOfRetries: Int): Response<ResponseBody> {
        var retry = maxNumberOfRetries
        while (true) {
            retry--
            try {
                return syncAPI.syncStream(
                        credentials,
                        params = requestParams
                ).awaitResponse()
            } catch (throwable: Throwable) {
                if (throwable is SocketTimeoutException && retry > 0) {
                    Timber.tag(loggerTag.value).w("INIT_SYNC timeout retry left: $retry")
                } else {
                    Timber.tag(loggerTag.value).e(throwable, "INIT_SYNC timeout, no retry left, or other error")
                    throw throwable
                }
            }
        }
    }

    private suspend fun handleSyncFile(workingFile: File, initSyncStrategy: InitialSyncStrategy.Optimized): SyncResponse {
        return logDuration("INIT_SYNC handleSyncFile()", loggerTag, clock) {
            val syncResponse = logDuration("INIT_SYNC Read file and parse", loggerTag, clock) {
                syncResponseParser.parse(initSyncStrategy, workingFile)
            }
            initialSyncStatusRepository.setStep(InitialSyncStatus.STEP_PARSED)
            // Log some stats
            val nbOfJoinedRooms = syncResponse.rooms?.join?.size ?: 0
            val nbOfJoinedRoomsInFile = syncResponse.rooms?.join?.values?.count { it.ephemeral is LazyRoomSyncEphemeral.Stored }
            Timber.tag(loggerTag.value).i("INIT_SYNC $nbOfJoinedRooms rooms, $nbOfJoinedRoomsInFile ephemeral stored into files")

            logDuration("INIT_SYNC Database insertion", loggerTag, clock) {
                syncResponseHandler.handleResponse(userId, syncResponse, null, afterPause = true, syncRequestStateTracker)
            }
            initialSyncStatusRepository.setStep(InitialSyncStatus.STEP_SUCCESS)
            syncResponse
        }
    }

    /**
     * Aggregator to send stat event.
     */
    class SyncStatisticsData(
            val isInitSync: Boolean,
            val isAfterPause: Boolean
    ) {
        val startTime = SystemClock.elapsedRealtime()
        var requestInitSyncTime = startTime
        var downloadInitSyncTime = startTime
        var treatmentSyncTime = startTime
        var nbOfRooms: Int = 0
    }

    private fun sendStatistics(data: SyncStatisticsData) {
        sendStatisticEvent(
                if (data.isInitSync) {
                    (StatisticEvent.InitialSyncRequest(
                            requestDurationMs = (data.requestInitSyncTime - data.startTime).toInt(),
                            downloadDurationMs = (data.downloadInitSyncTime - data.requestInitSyncTime).toInt(),
                            treatmentDurationMs = (data.treatmentSyncTime - data.downloadInitSyncTime).toInt(),
                            nbOfJoinedRooms = data.nbOfRooms,
                    ))
                } else {
                    StatisticEvent.SyncTreatment(
                            durationMs = (data.treatmentSyncTime - data.startTime).toInt(),
                            afterPause = data.isAfterPause,
                            nbOfJoinedRooms = data.nbOfRooms
                    )
                }
        )
    }

    private fun sendStatisticEvent(statisticEvent: StatisticEvent) {
        session.dispatchTo(sessionListeners) { session, listener ->
            listener.onStatisticsEvent(session, statisticEvent)
        }
    }

    companion object {
        private const val MAX_NUMBER_OF_RETRY_AFTER_TIMEOUT = 50

        private const val TIMEOUT_MARGIN: Long = 10_000
    }
}
