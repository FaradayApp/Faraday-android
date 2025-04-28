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
package io.matthewnelson.topl_service.service.components.receiver

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.RECEIVER_NOT_EXPORTED
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.os.Build
import androidx.core.content.ContextCompat
import io.matthewnelson.topl_service.service.BaseService
import io.matthewnelson.topl_service.service.TorService
import io.matthewnelson.topl_service.service.components.actions.ServiceAction
import io.matthewnelson.topl_service_base.BaseServiceConsts.ServiceActionName
import java.math.BigInteger
import java.security.SecureRandom

/**
 * Is registered at startup of [TorService], and unregistered when it is stopped.
 * Sending an intent here to start [TorService] will do nothing. To start the service (and Tor),
 * call the [io.matthewnelson.topl_service.TorServiceController.startTor] method.
 *
 * @param [torService]
 * */
internal class TorServiceReceiver private constructor(
    private val torService: BaseService
): BroadcastReceiver() {

    companion object {
        @JvmSynthetic
        fun instantiate(torService: BaseService): TorServiceReceiver =
            TorServiceReceiver(torService)

        // Secures the intent filter at each application startup.
        // Also serves as the key to string extras containing the ServiceAction to be executed.
        private val SERVICE_INTENT_FILTER: String by lazy {
            BigInteger(130, SecureRandom()).toString(32)
        }

        @JvmSynthetic
        fun getServiceIntentFilter(): String =
            SERVICE_INTENT_FILTER

        @Volatile
        private var isRegistered = false
        @JvmSynthetic
        fun isRegistered(): Boolean =
            isRegistered

        @Volatile
        private var deviceIsLocked: Boolean? = null
        @JvmSynthetic
        fun deviceIsLocked(): Boolean? =
            deviceIsLocked
    }

    private val broadcastLogger by lazy {
        torService.getBroadcastLogger(TorServiceReceiver::class.java)
    }

    @JvmSynthetic
    fun register() {
        val filter = IntentFilter(SERVICE_INTENT_FILTER)
        @Suppress("DEPRECATION")
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION)

        if (torService.doesReceiverNeedToListenForLockScreen()) {
            filter.addAction(Intent.ACTION_SCREEN_OFF)
            filter.addAction(Intent.ACTION_SCREEN_ON)
            filter.addAction(Intent.ACTION_USER_PRESENT)
        }

        val context = torService.getContext().applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(this, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(this, filter)
        }

        if (!isRegistered)
            broadcastLogger.debug("Has been registered")
        isRegistered = true
    }

    /**
     * Sets [deviceIsLocked] to the value sent. If [value] is null, it will check
     * KeyguardManager.
     * */
    @JvmSynthetic
    fun setDeviceIsLocked(value: Boolean? = null) {
        deviceIsLocked = value ?: checkIfDeviceIsLocked()
    }

    @JvmSynthetic
    fun unregister() {
        if (isRegistered) {
            try {
                torService.getContext().applicationContext.unregisterReceiver(this)
                isRegistered = false
                setDeviceIsLocked()
                broadcastLogger.debug("Has been unregistered")
            } catch (e: IllegalArgumentException) {
                broadcastLogger.exception(e)
            }
        }
    }

    private fun checkIfDeviceIsLocked(): Boolean? {
        val keyguardManager = torService.getContext().applicationContext
            .getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager?
        return keyguardManager?.isKeyguardLocked
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context != null && intent != null) {
            when (intent.action) {
                @Suppress("DEPRECATION")
                ConnectivityManager.CONNECTIVITY_ACTION -> {
                    if (!torService.isTorOn()) return

                    val connectivity = torService.hasNetworkConnectivity()
                    broadcastLogger.notice("Network connectivity: $connectivity")

                    val actionObject = if (connectivity) {
                        ServiceAction.SetDisableNetwork.instantiate(ServiceActionName.ENABLE_NETWORK)
                    } else {
                        ServiceAction.SetDisableNetwork.instantiate(ServiceActionName.DISABLE_NETWORK)
                    }

                    torService.processServiceAction(actionObject)
                }
                Intent.ACTION_SCREEN_OFF,
                Intent.ACTION_SCREEN_ON,
                Intent.ACTION_USER_PRESENT -> {
                    val locked = checkIfDeviceIsLocked()
                    if (locked != deviceIsLocked) {
                        setDeviceIsLocked(locked)
                        broadcastLogger.notice("Device is locked: $deviceIsLocked")
                        torService.refreshNotificationActions()
                    }
                }
                SERVICE_INTENT_FILTER -> {

                    when (val serviceAction = intent.getStringExtra(SERVICE_INTENT_FILTER)) {
                        ServiceActionName.NEW_ID -> {
                            torService.processServiceAction(ServiceAction.NewId.instantiate())
                        }
                        ServiceActionName.RESTART_TOR -> {
                            torService.processServiceAction(ServiceAction.RestartTor.instantiate())
                        }
                        ServiceActionName.STOP -> {
                            torService.processServiceAction(ServiceAction.Stop.instantiate())
                        }
                        else -> {
                            broadcastLogger.warn(
                                "This class does not accept $serviceAction as an argument."
                            )
                        }
                    }

                }
            }
        }
    }
}
