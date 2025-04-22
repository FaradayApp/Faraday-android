/*
 * Copyright 2019-2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package im.vector.app

import androidx.lifecycle.LifecycleOwner
import de.spiritcroc.matrixsdk.util.DbgUtil
import im.vector.app.core.di.ActiveSessionHolder
import im.vector.app.core.utils.BehaviorDataSource
import im.vector.app.features.analytics.AnalyticsTracker
import im.vector.app.features.analytics.plan.UserProperties
import im.vector.app.features.analytics.plan.ViewRoom
import im.vector.app.features.session.coroutineScope
import im.vector.app.features.settings.VectorPreferences
import im.vector.app.features.ui.UiStateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.matrix.android.sdk.api.extensions.tryOrNull
import org.matrix.android.sdk.api.session.Session
import org.matrix.android.sdk.api.session.getRoom
import org.matrix.android.sdk.api.session.getRoomSummary
import org.matrix.android.sdk.api.session.room.model.RoomSummary
import org.matrix.android.sdk.api.session.sync.SyncRequestState
import org.matrix.android.sdk.api.util.Optional
import org.matrix.android.sdk.api.util.toOption
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

enum class SelectSpaceFrom {
    // Initialized / uiStateRepository
    INIT,
    // Swiped in home pager
    SWIPE,
    // Persisted after swipe in home pager
    PERSIST_SWIPE,
    // Selected from non-pager UI
    SELECT,
}

/**
 * This class handles the global app state.
 * It is required that this class is added as an observer to ProcessLifecycleOwner.get().lifecycle in [VectorApplication]
 */
@Singleton
class SpaceStateHandlerImpl @Inject constructor(
        private val sessionDataSource: ActiveSessionDataSource,
        private val uiStateRepository: UiStateRepository,
        private val activeSessionHolder: ActiveSessionHolder,
        private val analyticsTracker: AnalyticsTracker,
        private val vectorPreferences: VectorPreferences,
) : SpaceStateHandler {

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    //private val selectedSpaceDataSource = BehaviorDataSource<Optional<RoomSummary>>(Optional.empty())

    // SC: Call it different then the upstream one so we don't forget adding `first` to upstream's logic.
    private val selectedSpaceDataSourceSc = BehaviorDataSource<Optional<Pair<RoomSummary?, SelectSpaceFrom>>>(Optional.empty())
    //private val selectedSpaceFlow = selectedSpaceDataSource.stream()
    private val selectedSpaceFlow = selectedSpaceDataSourceSc.stream().map { it.map { it.first } }
    private val selectedSpaceFlowIgnoreSwipe = selectedSpaceDataSourceSc.stream()
            .filter { it.orNull()?.second != SelectSpaceFrom.SWIPE }

    override fun getCurrentSpace(): RoomSummary? {
        return selectedSpaceDataSourceSc.currentValue?.orNull()?.first?.let { spaceSummary ->
            activeSessionHolder.getSafeActiveSession()?.roomService()?.getRoomSummary(spaceSummary.roomId)
        }
    }

    override fun setCurrentSpace(
            spaceId: String?,
            session: Session?,
            persistNow: Boolean,
            isForwardNavigation: Boolean,
            from: SelectSpaceFrom,
    ) {
        val activeSession = session ?: activeSessionHolder.getSafeActiveSession() ?: return
        val currentValue = selectedSpaceDataSourceSc.currentValue?.orNull()
        val spaceToLeave = currentValue?.first
        val spaceToSet = spaceId?.let { activeSession.getRoomSummary(spaceId) }
        val sameSpaceSelected = currentValue != null && spaceId == spaceToLeave?.roomId

        if (sameSpaceSelected) {
            return
        }

        if (DbgUtil.isDbgEnabled(DbgUtil.DBG_VIEW_PAGER) && from == SelectSpaceFrom.SELECT) {
            // We want a stack trace
            Timber.w(Exception("Home pager: setCurrentSpace/SELECT"))
        }

        analyticsTracker.capture(
                ViewRoom(
                        isDM = false,
                        isSpace = true,
                )
        )

        if (isForwardNavigation && from in listOf(SelectSpaceFrom.SELECT, SelectSpaceFrom.INIT)) {
            addToBackstack(spaceToLeave, spaceToSet)
        }

        if (persistNow) {
            uiStateRepository.storeSelectedSpace(spaceToSet?.roomId, activeSession.sessionId)
        }

        if (spaceToSet == null) {
            //selectedSpaceDataSourceSc.post(Option.empty())
            selectedSpaceDataSourceSc.post(Pair(null, from).toOption())
        } else {
            selectedSpaceDataSourceSc.post(Pair(spaceToSet, from).toOption())
        }

        if (spaceId != null) {
            activeSession.coroutineScope.launch(Dispatchers.IO) {
                tryOrNull {
                    activeSession.getRoom(spaceId)?.membershipService()?.loadRoomMembersIfNeeded()
                }
            }
        }
    }

    private fun addToBackstack(spaceToLeave: RoomSummary?, spaceToSet: RoomSummary?) {
        // Only add to the backstack if the space to set is not All Chats, else clear the backstack
        if (spaceToSet != null) {
            val currentPersistedBackstack = vectorPreferences.getSpaceBackstack().toMutableList()
            currentPersistedBackstack.add(spaceToLeave?.roomId)
            vectorPreferences.setSpaceBackstack(currentPersistedBackstack)
        } else {
            vectorPreferences.setSpaceBackstack(emptyList())
        }
    }

    private fun observeActiveSession() {
        sessionDataSource.stream()
                .distinctUntilChanged()
                .onEach {
                    // sessionDataSource could already return a session while activeSession holder still returns null
                    it.orNull()?.let { session ->
                        setCurrentSpace(uiStateRepository.getSelectedSpace(session.sessionId), session, from = SelectSpaceFrom.INIT)
                        //observeSyncStatus(session)
                    }
                }
                .launchIn(coroutineScope)
    }

    /*
    private fun observeSyncStatus(session: Session) {
        session.syncService().getSyncRequestStateFlow()
                .filterIsInstance<SyncRequestState.IncrementalSyncDone>()
                .map { session.spaceService().getRootSpaceSummaries().size }
                .distinctUntilChanged()
                .onEach { spacesNumber ->
                    analyticsTracker.updateUserProperties(UserProperties(numSpaces = spacesNumber))
                }.launchIn(session.coroutineScope)
    }
    */

    override fun popSpaceBackstack(): String? {
        vectorPreferences.getSpaceBackstack().toMutableList().apply {
            val poppedSpaceId = removeLast()
            vectorPreferences.setSpaceBackstack(this)
            return poppedSpaceId
        }
    }

    override fun getSpaceBackstack() = vectorPreferences.getSpaceBackstack()

    override fun getSelectedSpaceFlow() = selectedSpaceFlow

    override fun getSelectedSpaceFlowIgnoreSwipe() = selectedSpaceFlowIgnoreSwipe

    override fun getSafeActiveSpaceId(): String? {
        return selectedSpaceDataSourceSc.currentValue?.orNull()?.first?.roomId
    }

    override fun onResume(owner: LifecycleOwner) {
        observeActiveSession()
    }

    override fun onPause(owner: LifecycleOwner) {
        coroutineScope.coroutineContext.cancelChildren()
        val session = activeSessionHolder.getSafeActiveSession() ?: return
        uiStateRepository.storeSelectedSpace(selectedSpaceDataSourceSc.currentValue?.orNull()?.first?.roomId, session.sessionId)
    }

    override fun persistSelectedSpace() {
        val currentValue = selectedSpaceDataSourceSc.currentValue?.orNull() ?: return
        val currentMethod = currentValue.first
        val uSession = activeSessionHolder.getSafeActiveSession() ?: return

        // We want to persist it, so we also want to remove the pendingSwipe status
        if (currentValue.second == SelectSpaceFrom.SWIPE) {
            selectedSpaceDataSourceSc.post(Pair(currentMethod, SelectSpaceFrom.PERSIST_SWIPE).toOption())
        }

        // Persist it across app restarts
        uiStateRepository.storeSelectedSpace(currentMethod?.roomId, uSession.sessionId)
    }
}
