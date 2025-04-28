/*
* TorOnionProxyLibrary-Android (a.k.a. topl-android) is a derivation of
* work from the Tor_Onion_Proxy_Library project that started at commit
* hash `74407114cbfa8ea6f2ac51417dda8be98d8aba86`. Contributions made after
* said commit hash are:
*
*     Copyright (C) 2020 Matthew Nelson
*
*     This program is free software: you can redistribute it and/or modify it
*     under the terms of the GNU General Public License as published by the
*     Free Software Foundation, either version 3 of the License, or (at your
*     option) any later version.
*
*     This program is distributed in the hope that it will be useful, but
*     WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
*     or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
*     for more details.
*
*     You should have received a copy of the GNU General Public License
*     along with this program. If not, see <https://www.gnu.org/licenses/>.
*
* `===========================================================================`
* `+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++`
* `===========================================================================`
*
* The following exception is an additional permission under section 7 of the
* GNU General Public License, version 3 (“GPLv3”).
*
*     "The Interfaces" is henceforth defined as Application Programming Interfaces
*     needed to implement TorOnionProxyLibrary-Android, as listed below:
*
*      - From the `topl-core-base` module:
*          - All Classes/methods/variables
*
*      - From the `topl-service-base` module:
*          - All Classes/methods/variables
*
*      - From the `topl-service` module:
*          - The TorServiceController class and it's contained classes/methods/variables
*          - The ServiceNotification.Builder class and it's contained classes/methods/variables
*          - The BackgroundManager.Builder class and it's contained classes/methods/variables
*          - The BackgroundManager.Companion class and it's contained methods/variables
*
*     The following code is excluded from "The Interfaces":
*
*       - All other code
*
*     Linking TorOnionProxyLibrary-Android statically or dynamically with other
*     modules is making a combined work based on TorOnionProxyLibrary-Android.
*     Thus, the terms and conditions of the GNU General Public License cover the
*     whole combination.
*
*     As a special exception, the copyright holder of TorOnionProxyLibrary-Android
*     gives you permission to combine TorOnionProxyLibrary-Android program with free
*     software programs or libraries that are released under the GNU LGPL and with
*     independent modules that communicate with TorOnionProxyLibrary-Android solely
*     through "The Interfaces". You may copy and distribute such a system following
*     the terms of the GNU GPL for TorOnionProxyLibrary-Android and the licenses of
*     the other code concerned, provided that you include the source code of that
*     other code when and as the GNU GPL requires distribution of source code and
*     provided that you do not modify "The Interfaces".
*
*     Note that people who make modified versions of TorOnionProxyLibrary-Android
*     are not obligated to grant this special exception for their modified versions;
*     it is their choice whether to do so. The GNU General Public License gives
*     permission to release a modified version without this exception; this exception
*     also makes it possible to release a modified version which carries forward this
*     exception. If you modify "The Interfaces", this exception does not apply to your
*     modified version of TorOnionProxyLibrary-Android, and you must remove this
*     exception when you distribute your modified version.
* */
package io.matthewnelson.topl_service.service.components.onionproxy

import io.matthewnelson.topl_core.OnionProxyManager
import io.matthewnelson.topl_core_base.EventBroadcaster
import io.matthewnelson.topl_service.TorServiceController
import io.matthewnelson.topl_service.service.BaseService
import io.matthewnelson.topl_service.service.components.actions.ServiceActionProcessor
import io.matthewnelson.topl_service.service.TorService
import io.matthewnelson.topl_service_base.TorPortInfo
import io.matthewnelson.topl_service.util.ServiceConsts.NotificationImage
import io.matthewnelson.topl_service_base.BaseServiceConsts.ServiceActionName
import io.matthewnelson.topl_service_base.ServiceUtilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.freehaven.tor.control.TorControlCommands

/**
 * [io.matthewnelson.topl_core.OnionProxyManager] utilizes this customized class for
 * broadcasting things while it is operating (such as Tor's State, operation errors,
 * debugging, etc).
 *
 * [ServiceEventListener] utilizes this class by sending it what Tor is spitting out
 * (selectively curated, ofc).
 *
 * @param [torService] [BaseService] for context.
 * */
internal class ServiceEventBroadcaster private constructor(
    private val torService: BaseService
): EventBroadcaster() {

    companion object {
        @JvmSynthetic
        fun instantiate(torService: BaseService): ServiceEventBroadcaster =
            ServiceEventBroadcaster(torService)
    }

    private val scopeMain: CoroutineScope
        get() = torService.getScopeMain()

    /////////////////
    /// Bandwidth ///
    /////////////////
    @Volatile
    private var bytesRead = 0L
    @Volatile
    private var bytesWritten = 0L

    override fun broadcastBandwidth(bytesRead: String, bytesWritten: String) {
        val read =
            try {
                bytesRead.toLong()
            } catch (e: NumberFormatException) {
                this.bytesRead
            }
        val written =
            try {
                bytesWritten.toLong()
            } catch (e: NumberFormatException) {
                this.bytesWritten
            }

        // Only update the notification if proper State is had & we're bootstrapped.
        if (torState == TorState.ON &&
            torNetworkState == TorNetworkState.ENABLED &&
            isBootstrappingComplete()
        ) {
            if (read != this.bytesRead || written != this.bytesWritten) {
                this.bytesRead = read
                this.bytesWritten = written

                updateBandwidth(read, written)

                if (read == 0L && written == 0L)
                    torService.updateNotificationIcon(NotificationImage.ENABLED)
                else
                    torService.updateNotificationIcon(NotificationImage.DATA)
            }
        }

        TorServiceController.appEventBroadcaster?.let {
            scopeMain.launch { it.broadcastBandwidth(bytesRead, bytesWritten) }
        }
    }

    /**
     * Do a check for if a message is being displayed in the contentText of the
     * notification, allowing it to remain there unabated until the coroutine
     * finishes.
     * */
    private fun updateBandwidth(download: Long, upload: Long) {
        if (noticeMsgToContentTextJob?.isActive == true) return
        torService.updateNotificationContentText(
            ServiceUtilities.getFormattedBandwidthString(download, upload)
        )
    }


    /////////////
    /// Debug ///
    /////////////
    override fun broadcastDebug(msg: String) {
        TorServiceController.appEventBroadcaster?.let {
            scopeMain.launch { it.broadcastDebug(msg) }
        }
    }


    //////////////////
    /// Exceptions ///
    //////////////////
    override fun broadcastException(msg: String?, e: Exception) {
        if (!msg.isNullOrEmpty()) {
            if (msg.contains(TorService::class.java.simpleName)) {
                torService.updateNotificationIcon(NotificationImage.ERROR)
                val msgSplit = msg.split("|")
                msgSplit.elementAtOrNull(2)?.let {
                    torService.updateNotificationContentText(it)
                    torService.updateNotificationProgress(false, null)
                }
            }
        }

        TorServiceController.appEventBroadcaster?.let {
            scopeMain.launch { it.broadcastException(msg, e) }
        }
    }


    ///////////////////
    /// LogMessages ///
    ///////////////////
    override fun broadcastLogMessage(logMessage: String?) {
        TorServiceController.appEventBroadcaster?.let {
            scopeMain.launch { it.broadcastLogMessage(logMessage) }
        }
    }


    ///////////////
    /// Notices ///
    ///////////////
    private var noticeMsgToContentTextJob: Job? = null

    @Volatile
    private var bootstrapProgress = ""
    private fun isBootstrappingComplete(): Boolean =
        bootstrapProgress == "Bootstrapped 100%"

    @Volatile
    private var controlPort: String? = null
    @Volatile
    private var dnsPort: String? = null
    @Volatile
    private var httpTunnelPort: String? = null
    @Volatile
    private var socksPort: String? = null
    @Volatile
    private var transPort: String? = null

    private fun setAllPortsNull() {
        controlPort = null
        dnsPort = null
        httpTunnelPort = null
        socksPort = null
        transPort = null
    }

    override fun broadcastNotice(msg: String) {

        when {
            // ServiceActionProcessor
            msg.contains(ServiceActionProcessor::class.java.simpleName) -> {
                handleServiceActionProcessorMsg(msg)
            }
            // WARN|OnionProxyManager|No Network Connectivity. Foregoing enabling of Tor Network.
            msg.contains("No Network Connectivity. Foregoing enabling of Tor Network.") -> {
                torService.updateNotificationProgress(false, null)
                torService.updateNotificationContentText("No Network Connectivity. Waiting...")
            }
            // BOOTSTRAPPED
            msg.contains("Bootstrapped") -> {
                handleBootstrappedMsg(msg)
            }
            // Control Port
            // NOTICE|OnionProxyManager|Successfully connected to Control Port: 44201
            msg.contains("Successfully connected to Control Port:") -> {
                controlPort = getPortFromMsg(msg)
                if (isBootstrappingComplete())
                    updateAppEventBroadcasterWithPortInfo()
            }
            // Dns Port
            // NOTICE|OnionProxyManager|Opened DNS listener on 127.0.0.1:5400
            msg.contains("Opened DNS listener ") -> {
                dnsPort = getPortFromMsg(msg)
                if (isBootstrappingComplete())
                    updateAppEventBroadcasterWithPortInfo()
            }
            // Http Tunnel Port
            // NOTICE|BaseEventListener|Opened HTTP tunnel listener on 127.0.0.1:8118
            msg.contains("Opened HTTP tunnel listener ") -> {
                httpTunnelPort = getPortFromMsg(msg)
                if (isBootstrappingComplete())
                    updateAppEventBroadcasterWithPortInfo()
            }
            // Socks Port
            // NOTICE|BaseEventListener|Opened Socks listener on 127.0.0.1:9050
            msg.contains("Opened Socks listener ") -> {
                socksPort = getPortFromMsg(msg)
                if (isBootstrappingComplete())
                    updateAppEventBroadcasterWithPortInfo()
            }
            // Trans Port
            // NOTICE|BaseEventListener|Opened Transparent pf/netfilter listener on 127.0.0.1:9040
            msg.contains("Opened Transparent pf/netfilter listener ") -> {
                transPort = getPortFromMsg(msg)
                if (isBootstrappingComplete())
                    updateAppEventBroadcasterWithPortInfo()
            }
            // NEWNYM
            msg.contains(TorControlCommands.SIGNAL_NEWNYM) -> {
                handleNewNymMsg(msg)
            }
        }

        TorServiceController.appEventBroadcaster?.let {
            scopeMain.launch { it.broadcastNotice(msg) }
        }
    }

    // NOTICE|BaseEventListener|Bootstrapped 5% (conn): Connecting to a relay
    private fun handleBootstrappedMsg(msg: String) {
        val msgSplit = msg.split(" ")
        msgSplit.elementAtOrNull(2)?.let {
            val bootstrapped = "${msgSplit[0]} ${msgSplit[1]}".split("|")[2]

            if (bootstrapped != bootstrapProgress) {
                torService.updateNotificationContentText(bootstrapped)

                if (bootstrapped == "Bootstrapped 100%") {
                    updateAppEventBroadcasterWithPortInfo()
                    torService.updateNotificationIcon(NotificationImage.ENABLED)
                    torService.updateNotificationProgress(true, 100)
                    torService.updateNotificationProgress(false, null)
                    torService.addNotificationActions()
                } else {
                    val progress: Int? = try {
                        bootstrapped.split(" ")[1].split("%")[0].toInt()
                    } catch (e: Exception) {
                        null
                    }
                    progress?.let {
                        torService.updateNotificationProgress(true, progress)
                    }
                }

                bootstrapProgress = bootstrapped
            }
        }
    }

    private fun getPortFromMsg(msg: String): String =
        "127.0.0.1:${msg.split(":")[1].trim()}"

    private fun handleNewNymMsg(msg: String) {
        val msgToShow: String? =
            when {
                msg.contains(OnionProxyManager.NEWNYM_SUCCESS_MESSAGE) -> {
                    OnionProxyManager.NEWNYM_SUCCESS_MESSAGE
                }
                msg.contains(OnionProxyManager.NEWNYM_NO_NETWORK) -> {
                    OnionProxyManager.NEWNYM_NO_NETWORK
                }
                else -> {
                    val msgSplit = msg.split("|")
                    msgSplit.elementAtOrNull(2)
                }
            }

        if (noticeMsgToContentTextJob?.isActive == true)
            noticeMsgToContentTextJob?.cancel()

        msgToShow?.let {
            displayMessageToContentText(it, 3500L)
        }
    }

    private fun handleServiceActionProcessorMsg(msg: String) {
        val msgSplit = msg.split("|")
        val msgToShow: String? = msgSplit.elementAtOrNull(2)?.let {
            when (it) {
                ServiceActionName.RESTART_TOR -> {
                    "Restarting Tor..."
                }
                ServiceActionName.START -> {
                    // Need to check here if Tor is already on, as startTor can be called
                    // anytime which would overwrite the contentText already showing and
                    // then stay there until something else updates it.
                    if (torState != TorState.ON)
                        "Starting Tor..."
                    else
                        null
                }
                ServiceActionName.STOP -> {
                    "Stopping Service..."
                }
                else -> {
                    null
                }
            }
        }
        msgToShow?.let {
            torService.updateNotificationContentText(it)
        }
    }

    private var broadcastPortInfoJob: Job? = null
    private fun updateAppEventBroadcasterWithPortInfo() {
        if (broadcastPortInfoJob?.isActive == true)
            broadcastPortInfoJob?.cancel()

        TorServiceController.appEventBroadcaster?.let {
            broadcastPortInfoJob = scopeMain.launch {
                delay(100L)
                it.broadcastPortInformation(
                    TorPortInfo(
                        controlPort,
                        dnsPort,
                        httpTunnelPort,
                        socksPort,
                        transPort
                    )
                )
            }
        }
    }

    /**
     * Display a message in the notification's ContentText space for the defined
     * [delayMilliSeconds], after which (if Tor is connected), publish to the Notification's
     * ContentText the most recently broadcast bandwidth via [bytesRead] && [bytesWritten].
     * */
    private fun displayMessageToContentText(message: String, delayMilliSeconds: Long) {
        noticeMsgToContentTextJob = scopeMain.launch {
            torService.updateNotificationContentText(message)
            delay(delayMilliSeconds)

            // Publish the last bandwidth broadcast to overwrite the message.
            if (torNetworkState == TorNetworkState.ENABLED) {
                torService.updateNotificationContentText(
                    ServiceUtilities.getFormattedBandwidthString(bytesRead, bytesWritten)
                )
            } else if (isBootstrappingComplete()){
                torService.updateNotificationContentText(
                    ServiceUtilities.getFormattedBandwidthString(0L, 0L)
                )
            }
        }
    }


    ////////////////
    /// TorState ///
    ////////////////
    @Volatile
    private var torState = TorState.OFF
    @Volatile
    private var torNetworkState = TorNetworkState.DISABLED

    override fun broadcastTorState(@TorState state: String, @TorNetworkState networkState: String) {
        if (torState == TorState.ON && state != torState) {
            bootstrapProgress = ""
            setAllPortsNull()
            updateAppEventBroadcasterWithPortInfo()
            torService.removeNotificationActions()
        }

        if (state != TorState.ON)
            torService.updateNotificationProgress(true, null)

        torService.updateNotificationContentTitle(state)
        torState = state

        if (networkState == TorNetworkState.DISABLED) {
            if (isBootstrappingComplete()) {
                setAllPortsNull()
                updateAppEventBroadcasterWithPortInfo()
            }

            // Update torNetworkState _before_ setting the icon to `disabled` so bandwidth won't
            // overwrite the icon with an update
            torNetworkState = networkState
            torService.updateNotificationIcon(NotificationImage.DISABLED)
        } else {
            if (isBootstrappingComplete())
                torService.updateNotificationIcon(NotificationImage.ENABLED)

            // Update torNetworkState _after_ setting the icon to `enabled` so bandwidth changes
            // occur afterwards and this won't overwrite ImageState.DATA
            torNetworkState = networkState
        }

        TorServiceController.appEventBroadcaster?.let {
            scopeMain.launch { it.broadcastTorState(state, networkState) }
        }
    }
}