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
package io.matthewnelson.topl_service.service.components.actions

import io.matthewnelson.topl_service.service.BaseService
import io.matthewnelson.topl_service.util.ServiceConsts
import kotlinx.coroutines.*

/**
 * This class allows for sequential execution of individual [ServiceConsts.ServiceActionCommand]'s
 * for each [ServiceAction] and the ability to quickly interrupt execution for reacting to User
 * actions (such as stopping, or clearing the task). Because there are various ways to interact
 * with a Service (via Binding, BroadcastReceiver, or startService), this class acts as the funnel
 * to standardize things.
 *
 * @param [torService] [BaseService] for interacting with other components of the Service
 * @see [ServiceAction]
 * */
internal class ServiceActionProcessor private constructor(
    private val torService: BaseService
): ServiceConsts() {

    companion object {
        @JvmSynthetic
        fun instantiate(torService: BaseService): ServiceActionProcessor =
            ServiceActionProcessor(torService)

        private var disableNetworkDelay = 6_000L
        @JvmSynthetic
        fun getDisableNetworkDelay(): Long =
            disableNetworkDelay

        private var restartTorDelayTime = 500L
        @JvmSynthetic
        fun getRestartTorDelayTime(): Long =
            restartTorDelayTime

        private var stopServiceDelayTime = 100L
        @JvmSynthetic
        fun getStopServiceDelayTime(): Long =
            stopServiceDelayTime

        @JvmSynthetic
        fun initialize(
            disableNetworkMilliseconds: Long,
            restartMilliseconds: Long,
            stopServiceMilliseconds: Long
        ) {
            // Only initialize it once.
            try {
                BaseService.getAppContext()
            } catch (e: RuntimeException) {
                disableNetworkDelay = disableNetworkMilliseconds
                restartTorDelayTime = restartMilliseconds
                stopServiceDelayTime = stopServiceMilliseconds
            }
        }

        //////////////////////////
        /// Last ServiceAction ///
        //////////////////////////
        @Volatile
        @ServiceActionName
        private var lastServiceAction: String = ServiceActionName.STOP

        @JvmSynthetic
        fun wasLastAcceptedServiceActionStop(): Boolean =
            lastServiceAction == ServiceActionName.STOP
    }

    private val broadcastLogger = torService.getBroadcastLogger(ServiceActionProcessor::class.java)

    @JvmSynthetic
    fun processServiceAction(serviceAction: ServiceAction) {
        when (serviceAction) {
            is ServiceAction.NewId -> {
                removeActionFromQueueByName(
                    arrayOf(ServiceActionName.NEW_ID)
                )
            }
            is ServiceAction.RestartTor -> {
                removeActionFromQueueByName(
                    arrayOf(ServiceActionName.DISABLE_NETWORK)
                )
            }
            is ServiceAction.SetDisableNetwork -> {
                removeActionFromQueueByName(
                    arrayOf(ServiceActionName.DISABLE_NETWORK, ServiceActionName.ENABLE_NETWORK)
                )
            }
            is ServiceAction.Stop -> {
                torService.unbindTorService()
                torService.unregisterReceiver()
                clearActionQueue()
                broadcastLogger.notice(serviceAction.name)
            }
            is ServiceAction.Start -> {
                clearActionQueue()
                torService.stopForegroundService()
                torService.registerReceiver()
            }
        }

        if (serviceAction.updateLastAction)
            lastServiceAction = serviceAction.name

        if (addActionToQueue(serviceAction))
            launchProcessQueueJob()
    }

    private fun broadcastDebugMsgWithObjectDetails(prefix: String, something: Any) {
        broadcastLogger.debug(
            "$prefix${something.javaClass.simpleName}@${something.hashCode()}"
        )
    }


    ////////////////////
    /// Action Queue ///
    ////////////////////
    private val actionQueueLock = Object()
    private val actionQueue = mutableListOf<ServiceAction>()

    private fun addActionToQueue(serviceAction: ServiceAction): Boolean =
        synchronized(actionQueueLock) {
            return if (actionQueue.add(serviceAction)) {
                broadcastDebugMsgWithObjectDetails(
                    "Added to queue: ServiceAction.", serviceAction
                )
                true
            } else {
                false
            }
        }

    private fun clearActionQueue() {
        synchronized(actionQueueLock) {
            if (actionQueue.isNullOrEmpty()) return

            actionQueue.clear()
            broadcastLogger.debug("Queue cleared")
        }
    }

    private fun getActionQueueElementAtOrNull(): ServiceAction? =
        synchronized(actionQueueLock) {
            actionQueue.elementAtOrNull(0)
        }

    private fun removeActionFromQueue(serviceAction: ServiceAction) {
        synchronized(actionQueueLock) {
            if (actionQueue.remove(serviceAction))
                broadcastDebugMsgWithObjectDetails(
                    "Removed from queue: ServiceAction.", serviceAction
                )
        }
    }

    private fun removeActionFromQueueByName(serviceActionNames: Array<@ServiceActionName String>) {
        synchronized(actionQueueLock) {
            if (serviceActionNames.isNullOrEmpty() || actionQueue.isNullOrEmpty()) return

            val queueIterator = actionQueue.iterator()
            while (queueIterator.hasNext()) {
                val next = queueIterator.next()
                var removed = false

                serviceActionNames.forEach { serviceActionName ->
                    if (!removed && next.name == serviceActionName) {
                        queueIterator.remove()
                        broadcastDebugMsgWithObjectDetails(
                            "Removed from queue: ServiceAction.", next
                        )
                        removed = true
                    }
                }
            }
        }
    }


    ////////////////////////
    /// Queue Processing ///
    ////////////////////////
    private var processQueueJob: Job? = null

    private fun launchProcessQueueJob() {
        if (processQueueJob?.isActive == true) return

        processQueueJob = torService.getScopeIO().launch {

            broadcastDebugMsgWithObjectDetails("Processing Queue: ", this)

            while (actionQueue.isNotEmpty() && isActive) {
                torService.joinOnStartCommandExecutionHookJob()
                val serviceAction = getActionQueueElementAtOrNull()
                if (serviceAction == null) {
                    return@launch
                } else {
                    broadcastLogger.notice(serviceAction.name)
                    serviceAction.commands.forEachIndexed { index, command ->

                        // Check if the current actionObject being executed has been
                        // removed from the queue before executing it's next command.
                        if (getActionQueueElementAtOrNull() != serviceAction) {
                            broadcastDebugMsgWithObjectDetails(
                                "Interrupting execution of: ServiceAction.", serviceAction
                            )
                            return@forEachIndexed
                        }

                        when (command) {
                            ServiceActionCommand.DELAY -> {
                                var delayLength = serviceAction.consumeDelayLength()
                                broadcastLogger.debug("${command}: ${delayLength}L")

                                while (delayLength > 0) {
                                    if (getActionQueueElementAtOrNull() != serviceAction)
                                        break

                                    if (delayLength in 1..499)
                                        delay(delayLength)
                                    else
                                        delay(500L)

                                    delayLength -= 500L
                                }
                            }
                            ServiceActionCommand.SET_DISABLE_NETWORK -> {
                                torService.disableNetwork(
                                    serviceAction.name == ServiceActionName.DISABLE_NETWORK
                                )
                            }
                            ServiceActionCommand.NEW_ID -> {
                                torService.signalNewNym()
                            }
                            ServiceActionCommand.START_TOR -> {
                                if (!torService.hasControlConnection()) {
                                    torService.startTor()
                                }
                            }
                            ServiceActionCommand.STOP_SERVICE -> {
                                broadcastDebugMsgWithObjectDetails("Stopping: ", torService)
                                torService.stopService()
                            }
                            ServiceActionCommand.STOP_TOR -> {
                                if (torService.hasControlConnection()) {
                                    torService.stopTor()
                                }
                            }
                        }

                        if (index == serviceAction.commands.lastIndex)
                            removeActionFromQueue(serviceAction)
                    }
                }
            }
        }
    }
}