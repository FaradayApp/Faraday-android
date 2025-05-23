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

package org.matrix.android.sdk.internal.session

import android.content.Context
import android.os.Build
import com.zhuinden.monarchy.Monarchy
import dagger.Binds
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoSet
import io.realm.RealmConfiguration
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.matrix.android.sdk.api.MatrixConfiguration
import org.matrix.android.sdk.api.auth.data.Credentials
import org.matrix.android.sdk.api.auth.data.HomeServerConnectionConfig
import org.matrix.android.sdk.api.auth.data.SessionParams
import org.matrix.android.sdk.api.auth.data.sessionId
import org.matrix.android.sdk.api.crypto.MXCryptoConfig
import org.matrix.android.sdk.api.session.EventStreamService
import org.matrix.android.sdk.api.session.Session
import org.matrix.android.sdk.api.session.SessionLifecycleObserver
import org.matrix.android.sdk.api.session.ToDeviceService
import org.matrix.android.sdk.api.session.accountdata.SessionAccountDataService
import org.matrix.android.sdk.api.session.events.EventService
import org.matrix.android.sdk.api.session.homeserver.HomeServerCapabilitiesService
import org.matrix.android.sdk.api.session.openid.OpenIdService
import org.matrix.android.sdk.api.session.permalinks.PermalinkService
import org.matrix.android.sdk.api.session.securestorage.SharedSecretStorageService
import org.matrix.android.sdk.api.session.typing.TypingUsersTracker
import org.matrix.android.sdk.api.settings.LightweightSettingsStorage
import org.matrix.android.sdk.api.util.md5
import org.matrix.android.sdk.internal.crypto.secrets.DefaultSharedSecretStorageService
import org.matrix.android.sdk.internal.crypto.tasks.DefaultRedactEventTask
import org.matrix.android.sdk.internal.crypto.tasks.RedactEventTask
import org.matrix.android.sdk.internal.database.EventInsertLiveObserver
import org.matrix.android.sdk.internal.database.RealmSessionProvider
import org.matrix.android.sdk.internal.database.SessionRealmConfigurationFactory
import org.matrix.android.sdk.internal.di.Authenticated
import org.matrix.android.sdk.internal.di.CacheDirectory
import org.matrix.android.sdk.internal.di.DeviceId
import org.matrix.android.sdk.internal.di.MultiServer
import org.matrix.android.sdk.internal.di.ProxyProvider
import org.matrix.android.sdk.internal.di.SessionDatabase
import org.matrix.android.sdk.internal.di.SessionDownloadsDirectory
import org.matrix.android.sdk.internal.di.SessionFilesDirectory
import org.matrix.android.sdk.internal.di.SessionId
import org.matrix.android.sdk.internal.di.SessionRustFilesDirectory
import org.matrix.android.sdk.internal.di.Unauthenticated
import org.matrix.android.sdk.internal.di.UnauthenticatedWithCertificate
import org.matrix.android.sdk.internal.di.UnauthenticatedWithCertificateWithProgress
import org.matrix.android.sdk.internal.di.UserId
import org.matrix.android.sdk.internal.di.UserMd5
import org.matrix.android.sdk.internal.network.DefaultNetworkConnectivityChecker
import org.matrix.android.sdk.internal.network.FallbackNetworkCallbackStrategy
import org.matrix.android.sdk.internal.network.GlobalErrorHandler
import org.matrix.android.sdk.internal.network.GlobalErrorReceiver
import org.matrix.android.sdk.internal.network.HttpAuthenticator
import org.matrix.android.sdk.internal.network.MultiServerInterceptor
import org.matrix.android.sdk.internal.network.NetworkCallbackStrategy
import org.matrix.android.sdk.internal.network.NetworkConnectivityChecker
import org.matrix.android.sdk.internal.network.PreferredNetworkCallbackStrategy
import org.matrix.android.sdk.internal.network.RetrofitFactory
import org.matrix.android.sdk.internal.network.awaitResponse
import org.matrix.android.sdk.internal.network.httpclient.addAccessTokenInterceptor
import org.matrix.android.sdk.internal.network.httpclient.addSocketFactory
import org.matrix.android.sdk.internal.network.httpclient.applyMatrixConfiguration
import org.matrix.android.sdk.internal.network.interceptors.CurlLoggingInterceptor
import org.matrix.android.sdk.internal.network.token.AccessTokenProvider
import org.matrix.android.sdk.internal.network.token.HomeserverAccessTokenProvider
import org.matrix.android.sdk.internal.session.call.CallEventProcessor
import org.matrix.android.sdk.internal.session.download.DownloadProgressInterceptor
import org.matrix.android.sdk.internal.session.events.DefaultEventService
import org.matrix.android.sdk.internal.session.homeserver.DefaultHomeServerCapabilitiesService
import org.matrix.android.sdk.internal.session.identity.DefaultIdentityService
import org.matrix.android.sdk.internal.session.integrationmanager.IntegrationManager
import org.matrix.android.sdk.internal.session.media.DefaultIsAuthenticatedMediaSupported
import org.matrix.android.sdk.internal.session.openid.DefaultOpenIdService
import org.matrix.android.sdk.internal.session.permalinks.DefaultPermalinkService
import org.matrix.android.sdk.internal.session.room.EventRelationsAggregationProcessor
import org.matrix.android.sdk.internal.session.room.aggregation.poll.DefaultPollAggregationProcessor
import org.matrix.android.sdk.internal.session.room.aggregation.poll.PollAggregationProcessor
import org.matrix.android.sdk.internal.session.room.create.RoomCreateEventProcessor
import org.matrix.android.sdk.internal.session.room.location.LiveLocationShareRedactionEventProcessor
import org.matrix.android.sdk.internal.session.room.prune.RedactionEventProcessor
import org.matrix.android.sdk.internal.session.room.send.queue.EventSenderProcessor
import org.matrix.android.sdk.internal.session.room.send.queue.EventSenderProcessorCoroutine
import org.matrix.android.sdk.internal.session.room.tombstone.RoomTombstoneEventProcessor
import org.matrix.android.sdk.internal.session.typing.DefaultTypingUsersTracker
import org.matrix.android.sdk.internal.session.user.accountdata.DefaultSessionAccountDataService
import org.matrix.android.sdk.internal.session.widgets.DefaultWidgetURLFormatter
import org.matrix.android.sdk.internal.session.workmanager.DefaultWorkManagerConfig
import org.matrix.android.sdk.internal.session.workmanager.WorkManagerConfig
import retrofit2.Retrofit
import timber.log.Timber
import java.io.File
import javax.inject.Provider
import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
internal annotation class MockHttpInterceptor

@Module
internal abstract class SessionModule {

    @Module
    companion object {
        internal fun getKeyAlias(userMd5: String) = "session_db_$userMd5"

        /**
         * Rules:
         * Annotate methods with @SessionScope only the @Provides annotated methods with computation and logic.
         */

        @JvmStatic
        @Provides
        fun providesHomeServerConnectionConfig(sessionParams: SessionParams): HomeServerConnectionConfig {
            return sessionParams.homeServerConnectionConfig
        }

        @JvmStatic
        @Provides
        fun providesCredentials(sessionParams: SessionParams): Credentials {
            return sessionParams.credentials
        }

        @JvmStatic
        @UserId
        @Provides
        @SessionScope
        fun providesUserId(credentials: Credentials): String {
            return credentials.userId
        }

        @JvmStatic
        @DeviceId
        @Provides
        fun providesDeviceId(credentials: Credentials): String {
            return credentials.deviceId
        }

        @JvmStatic
        @UserMd5
        @Provides
        @SessionScope
        fun providesUserMd5(@UserId userId: String): String {
            return userId.md5()
        }

        @JvmStatic
        @SessionId
        @Provides
        @SessionScope
        fun providesSessionId(credentials: Credentials): String {
            return credentials.sessionId()
        }

        @JvmStatic
        @Provides
        @SessionFilesDirectory
        @SessionScope
        fun providesFilesDir(
                @UserMd5 userMd5: String,
                @SessionId sessionId: String,
                context: Context
        ): File {
            // Temporary code for migration
            val old = File(context.filesDir, userMd5)
            if (old.exists()) {
                old.renameTo(File(context.filesDir, sessionId))
            }

            return File(context.filesDir, sessionId)
        }

        @JvmStatic
        @Provides
        @SessionRustFilesDirectory
        @SessionScope
        fun providesRustCryptoFilesDir(
                @SessionFilesDirectory parent: File,
        ): File {
            return File(parent, "rustFlavor")
        }

        @JvmStatic
        @Provides
        @SessionDownloadsDirectory
        fun providesDownloadsCacheDir(
                @SessionId sessionId: String,
                @CacheDirectory cacheFile: File
        ): File {
            return File(cacheFile, "downloads/$sessionId")
        }

        @JvmStatic
        @Provides
        @SessionDatabase
        @SessionScope
        fun providesRealmConfiguration(realmConfigurationFactory: SessionRealmConfigurationFactory): RealmConfiguration {
            return realmConfigurationFactory.create()
        }

        @JvmStatic
        @Provides
        @SessionDatabase
        @SessionScope
        fun providesMonarchy(@SessionDatabase realmConfiguration: RealmConfiguration): Monarchy {
            return Monarchy.Builder()
                    .setRealmConfiguration(realmConfiguration)
                    .build()
        }

        @JvmStatic
        @Provides
        @SessionScope
        @UnauthenticatedWithCertificate
        fun providesOkHttpClientWithCertificate(
                @Unauthenticated okHttpClient: OkHttpClient,
                homeServerConnectionConfig: HomeServerConnectionConfig,
                lightweightSettingsStorage: LightweightSettingsStorage
        ): OkHttpClient {
            val proxy = ProxyProvider(lightweightSettingsStorage).providesProxy()
            return okHttpClient
                    .newBuilder()
                    .addSocketFactory(homeServerConnectionConfig)
                    .proxy(proxy)
                    .apply {
                        val username = lightweightSettingsStorage.getProxyUsername()
                        val password = lightweightSettingsStorage.getProxyPassword()
                        if (username.isNotEmpty() && password.isNotEmpty()) {
                            val credentials = okhttp3.Credentials.basic(username, password)
                            val authenticator = HttpAuthenticator(credentials = credentials, "Proxy-Authorization")
                            proxyAuthenticator(authenticator)
                        }
                    }
                    .build()
        }

        @JvmStatic
        @Provides
        @SessionScope
        @Authenticated
        fun providesOkHttpClient(
                @UnauthenticatedWithCertificate okHttpClient: OkHttpClient,
                @Authenticated accessTokenProvider: AccessTokenProvider,
                @SessionId sessionId: String,
                @MockHttpInterceptor testInterceptor: TestInterceptor?,
                matrixConfiguration: MatrixConfiguration,
//                multiServerInterceptor: MultiServerInterceptor,
                lightweightSettingsStorage: LightweightSettingsStorage
        ): OkHttpClient {
            val proxy = ProxyProvider(lightweightSettingsStorage).providesProxy()
            return okHttpClient
                    .newBuilder()
//                    .addInterceptor(multiServerInterceptor)
                    .addAccessTokenInterceptor(accessTokenProvider)
                    .apply {
                        if (testInterceptor != null) {
                            testInterceptor.sessionId = sessionId
                            addInterceptor(testInterceptor)
                        }
                    }
                    .applyMatrixConfiguration(matrixConfiguration)
                    .proxy(proxy)
                    .apply {
                        val username = lightweightSettingsStorage.getProxyUsername()
                        val password = lightweightSettingsStorage.getProxyPassword()
                        if (username.isNotEmpty() && password.isNotEmpty()) {
                            val credentials = okhttp3.Credentials.basic(username, password)
                            val authenticator = HttpAuthenticator(credentials = credentials, "Proxy-Authorization")
                            proxyAuthenticator(authenticator)
                        }
                    }
                    .build()
        }

        @OptIn(DelicateCoroutinesApi::class)
        @JvmStatic
        @Provides
        @SessionScope
        @MultiServer
        fun providesMultiServerOkHttpClient(
                @UnauthenticatedWithCertificate okHttpClient: OkHttpClient,
                @SessionId sessionId: String,
                @MockHttpInterceptor testInterceptor: TestInterceptor?,
                matrixConfiguration: MatrixConfiguration,
                multiServerInterceptor: MultiServerInterceptor,
                lightweightSettingsStorage: LightweightSettingsStorage
        ): OkHttpClient {
            val proxy = ProxyProvider(lightweightSettingsStorage).providesProxy()
            return okHttpClient
                    .newBuilder()
                    .addInterceptor(multiServerInterceptor)
                    .apply {
                        val existingCurlInterceptors = interceptors().filterIsInstance<CurlLoggingInterceptor>()
                        interceptors().removeAll(existingCurlInterceptors)

                        addInterceptor(multiServerInterceptor)

                        // Re add eventually the curl logging interceptors
                        existingCurlInterceptors.forEach {
                            addInterceptor(it)
                        }
                    }
                    .apply {
                        if (testInterceptor != null) {
                            testInterceptor.sessionId = sessionId
                            addInterceptor(testInterceptor)
                        }
                    }
                    .applyMatrixConfiguration(matrixConfiguration)
                    .proxy(proxy)
                    .apply {
                        val username = lightweightSettingsStorage.getProxyUsername()
                        val password = lightweightSettingsStorage.getProxyPassword()
                        if (username.isNotEmpty() && password.isNotEmpty()) {
                            val credentials = okhttp3.Credentials.basic(username, password)
                            val authenticator = HttpAuthenticator(credentials = credentials, "Proxy-Authorization")
                            proxyAuthenticator(authenticator)
                        }
                    }
                    .build().also {
                        GlobalScope.launch(Dispatchers.IO) {
                            try {
                                Timber.d(
                                        "Check tor:" + it.newCall(
                                                Request.Builder()
                                                        .url("http://il7uf3kkxkryrtzqqj5j2axo45byy6h7z44uyr66lx5hbfxjb5uf4yyd.onion")
                                                        .get()
                                                        .build()
                                        ).awaitResponse()
                                )
                            } catch (e: Throwable) {
                                Timber.d("Check tor: failed with $e")
                            }
                        }
                    }
        }

        @JvmStatic
        @Provides
        @SessionScope
        @UnauthenticatedWithCertificateWithProgress
        fun providesProgressOkHttpClient(
                @UnauthenticatedWithCertificate okHttpClient: OkHttpClient,
                downloadProgressInterceptor: DownloadProgressInterceptor,
                matrixConfiguration: MatrixConfiguration,
                lightweightSettingsStorage: LightweightSettingsStorage
        ): OkHttpClient {
            val proxy = ProxyProvider(lightweightSettingsStorage).providesProxy()
            return okHttpClient
                    .newBuilder()
                    .apply {
                        // Remove the previous CurlLoggingInterceptor, to add it after the downloadProgressInterceptor
                        val existingCurlInterceptors = interceptors().filterIsInstance<CurlLoggingInterceptor>()
                        interceptors().removeAll(existingCurlInterceptors)

                        addInterceptor(downloadProgressInterceptor)

                        // Re add eventually the curl logging interceptors
                        existingCurlInterceptors.forEach {
                            addInterceptor(it)
                        }
                    }
                    .applyMatrixConfiguration(matrixConfiguration)
                    .proxy(proxy)
                    .apply {
                        val username = lightweightSettingsStorage.getProxyUsername()
                        val password = lightweightSettingsStorage.getProxyPassword()
                        if (username.isNotEmpty() && password.isNotEmpty()) {
                            val credentials = okhttp3.Credentials.basic(username, password)
                            val authenticator = HttpAuthenticator(credentials = credentials, "Proxy-Authorization")
                            proxyAuthenticator(authenticator)
                        }
                    }
                    .build()
        }

        @JvmStatic
        @Provides
        @SessionScope
        fun providesRetrofit(
                @Authenticated okHttpClient: Lazy<OkHttpClient>,
                sessionParams: SessionParams,
                retrofitFactory: RetrofitFactory
        ): Retrofit {
            var uri = sessionParams.homeServerConnectionConfig.homeServerUriBase.toString()
            if (uri == "http://localhost:8080") {
                uri = "http://10.0.2.2:8080"
            } else if (uri == "http://localhost:8081") {
                uri = "http://10.0.2.2:8081"
            }
            return retrofitFactory
                    .create(okHttpClient, uri)
        }

        @JvmStatic
        @Provides
        @SessionScope
        @MultiServer
        fun providesMultiServerRetrofit(
                @MultiServer okHttpClient: Lazy<OkHttpClient>,
                sessionParams: SessionParams, // TODO: make something rationale with it, it default session?
                retrofitFactory: RetrofitFactory
        ): Retrofit {
            return retrofitFactory
                    .create(okHttpClient, sessionParams.homeServerConnectionConfig.homeServerUriBase.toString())
        }

        @JvmStatic
        @Provides
        @SessionScope
        fun providesNetworkCallbackStrategy(
                fallbackNetworkCallbackStrategy: Provider<FallbackNetworkCallbackStrategy>,
                preferredNetworkCallbackStrategy: Provider<PreferredNetworkCallbackStrategy>
        ): NetworkCallbackStrategy {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                preferredNetworkCallbackStrategy.get()
            } else {
                fallbackNetworkCallbackStrategy.get()
            }
        }

        @JvmStatic
        @Provides
        @SessionScope
        fun providesMxCryptoConfig(matrixConfiguration: MatrixConfiguration): MXCryptoConfig {
            return matrixConfiguration.cryptoConfig
        }
    }

    @Binds
    @Authenticated
    abstract fun bindAccessTokenProvider(provider: HomeserverAccessTokenProvider): AccessTokenProvider

    @Binds
    abstract fun bindSession(session: DefaultSession): Session

    @Binds
    abstract fun bindGlobalErrorReceiver(handler: GlobalErrorHandler): GlobalErrorReceiver

    @Binds
    abstract fun bindNetworkConnectivityChecker(checker: DefaultNetworkConnectivityChecker): NetworkConnectivityChecker

    @Binds
    @IntoSet
    abstract fun bindEventRedactionProcessor(processor: RedactionEventProcessor): EventInsertLiveProcessor

    @Binds
    @IntoSet
    abstract fun bindLiveLocationShareRedactionEventProcessor(processor: LiveLocationShareRedactionEventProcessor): EventInsertLiveProcessor

    @Binds
    @IntoSet
    abstract fun bindEventRelationsAggregationProcessor(processor: EventRelationsAggregationProcessor): EventInsertLiveProcessor

    @Binds
    @IntoSet
    abstract fun bindRoomTombstoneEventProcessor(processor: RoomTombstoneEventProcessor): EventInsertLiveProcessor

    @Binds
    @IntoSet
    abstract fun bindRoomCreateEventProcessor(processor: RoomCreateEventProcessor): EventInsertLiveProcessor

    @Binds
    @IntoSet
    abstract fun bindCallEventProcessor(processor: CallEventProcessor): EventInsertLiveProcessor

    @Binds
    @IntoSet
    abstract fun bindEventInsertObserver(observer: EventInsertLiveObserver): SessionLifecycleObserver

    @Binds
    @IntoSet
    abstract fun bindIsMediaAuthenticated(observer: DefaultIsAuthenticatedMediaSupported): SessionLifecycleObserver

    @Binds
    @IntoSet
    abstract fun bindIntegrationManager(manager: IntegrationManager): SessionLifecycleObserver

    @Binds
    @IntoSet
    abstract fun bindWidgetUrlFormatter(formatter: DefaultWidgetURLFormatter): SessionLifecycleObserver

    @Binds
    @IntoSet
    abstract fun bindIdentityService(service: DefaultIdentityService): SessionLifecycleObserver

    @Binds
    @IntoSet
    abstract fun bindRealmSessionProvider(provider: RealmSessionProvider): SessionLifecycleObserver

    @Binds
    @IntoSet
    abstract fun bindSessionCoroutineScopeHolder(holder: SessionCoroutineScopeHolder): SessionLifecycleObserver

    @Binds
    @IntoSet
    abstract fun bindEventSenderProcessorAsSessionLifecycleObserver(processor: EventSenderProcessorCoroutine): SessionLifecycleObserver

    @Binds
    abstract fun bindHomeServerCapabilitiesService(service: DefaultHomeServerCapabilitiesService): HomeServerCapabilitiesService

    @Binds
    abstract fun bindSessionAccountDataService(service: DefaultSessionAccountDataService): SessionAccountDataService

    @Binds
    abstract fun bindEventService(service: DefaultEventService): EventService

    @Binds
    abstract fun bindSharedSecretStorageService(service: DefaultSharedSecretStorageService): SharedSecretStorageService

    @Binds
    abstract fun bindPermalinkService(service: DefaultPermalinkService): PermalinkService

    @Binds
    abstract fun bindOpenIdTokenService(service: DefaultOpenIdService): OpenIdService

    @Binds
    abstract fun bindToDeviceService(service: DefaultToDeviceService): ToDeviceService

    @Binds
    abstract fun bindEventStreamService(service: DefaultEventStreamService): EventStreamService

    @Binds
    abstract fun bindTypingUsersTracker(tracker: DefaultTypingUsersTracker): TypingUsersTracker

    @Binds
    abstract fun bindRedactEventTask(task: DefaultRedactEventTask): RedactEventTask

    @Binds
    abstract fun bindEventSenderProcessor(processor: EventSenderProcessorCoroutine): EventSenderProcessor

    @Binds
    abstract fun bindPollAggregationProcessor(processor: DefaultPollAggregationProcessor): PollAggregationProcessor

    @Binds
    abstract fun bindWorkManaerConfig(config: DefaultWorkManagerConfig): WorkManagerConfig
}
