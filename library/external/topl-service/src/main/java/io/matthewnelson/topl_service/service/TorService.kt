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
package io.matthewnelson.topl_service.service

import android.content.*
import android.os.IBinder
import androidx.annotation.WorkerThread
import io.matthewnelson.topl_core.OnionProxyManager
import io.matthewnelson.topl_core.broadcaster.BroadcastLogger
import io.matthewnelson.topl_core.util.FileUtilities
import io.matthewnelson.topl_service.TorServiceController
import io.matthewnelson.topl_service.lifecycle.BackgroundManager
import io.matthewnelson.topl_service.service.components.binding.TorServiceBinder
import io.matthewnelson.topl_service.service.components.onionproxy.ServiceEventBroadcaster
import io.matthewnelson.topl_service.service.components.onionproxy.ServiceEventListener
import io.matthewnelson.topl_service.service.components.onionproxy.ServiceTorInstaller
import io.matthewnelson.topl_service.service.components.receiver.TorServiceReceiver
import kotlinx.coroutines.*
import java.io.File
import java.io.IOException
import java.lang.reflect.InvocationTargetException

/**
 * The glue for everything within the [io.matthewnelson.topl_service.service.components] package.
 *
 * @see [BaseService]
 * */
internal class TorService internal constructor(): BaseService() {

    @JvmSynthetic
    override fun getContext(): Context = this


    ///////////////
    /// Binding ///
    ///////////////
    private val torServiceBinder: TorServiceBinder by lazy {
        TorServiceBinder.instantiate(this)
    }

    @JvmSynthetic
    override fun unbindTorService() {
        try {
            unbindService(getContext())
            super.unbindTorService()
        } catch (e: IllegalArgumentException) {}
    }
    override fun onBind(intent: Intent?): IBinder? {
        super.onBind(null)
        return torServiceBinder
    }


    /////////////////////////
    /// BroadcastReceiver ///
    /////////////////////////
    private val torServiceReceiver by lazy {
        TorServiceReceiver.instantiate(this)
    }
    @JvmSynthetic
    override fun registerReceiver() {
        torServiceReceiver.register()
    }
    @JvmSynthetic
    override fun setIsDeviceLocked() {
        torServiceReceiver.setDeviceIsLocked()
    }
    @JvmSynthetic
    override fun unregisterReceiver() {
        torServiceReceiver.unregister()
    }


    //////////////////
    /// Coroutines ///
    //////////////////
    private val supervisorJob = SupervisorJob()
    private val scopeDefault = CoroutineScope(Dispatchers.Default + supervisorJob)
    private val scopeIO = CoroutineScope(Dispatchers.IO + supervisorJob)
    private val scopeMain = CoroutineScope(Dispatchers.Main + supervisorJob)

    @JvmSynthetic
    override fun getScopeDefault(): CoroutineScope {
        return scopeDefault
    }
    @JvmSynthetic
    override fun getScopeIO(): CoroutineScope {
        return scopeIO
    }
    @JvmSynthetic
    override fun getScopeMain(): CoroutineScope {
        return scopeMain
    }


    //////////////////////////////
    /// ServiceActionProcessor ///
    //////////////////////////////

    // See BaseService


    ///////////////////////////
    /// ServiceNotification ///
    ///////////////////////////

    // See BaseService

    @JvmSynthetic
    override fun refreshNotificationActions(): Boolean {
        val wasRefreshed = super.refreshNotificationActions()
        if (wasRefreshed) {
            val debugMsg = if (TorServiceReceiver.deviceIsLocked() == true)
                "Removed Notification Actions"
            else
                "Added Notification Actions"
            broadcastLogger.debug(debugMsg)
        }
        return wasRefreshed
    }
    @JvmSynthetic
    override fun startForegroundService(): Boolean {
        val wasStarted = super.startForegroundService()
        if (wasStarted) {
            broadcastLogger.debug("Service sent to Foreground")
        }
        return wasStarted
    }
    @JvmSynthetic
    override fun stopForegroundService(): Boolean {
        val wasStopped = super.stopForegroundService()
        if (wasStopped) {
            broadcastLogger.debug("Service sent to Background")
        }
        return wasStopped
    }


    /////////////////
    /// TOPL-Core ///
    /////////////////
    private val broadcastLogger: BroadcastLogger by lazy {
        getBroadcastLogger(TorService::class.java)
    }
    private val onionProxyManager: OnionProxyManager by lazy {
        OnionProxyManager(
            getContext(),
            TorServiceController.getTorConfigFiles(),
            ServiceTorInstaller.instantiate(this),
            TorServiceController.getServiceTorSettings(),
            ServiceEventListener.instantiate(),
            ServiceEventBroadcaster.instantiate(this),
            getBuildConfigDebug()
        )
    }

    @JvmSynthetic
    @WorkerThread
    @Throws(IOException::class)
    override fun copyAsset(assetPath: String, file: File) {
        try {
            FileUtilities.copy(getContext().assets.open(assetPath), file.outputStream())
        } catch (e: Exception) {
            throw IOException("Failed copying asset from $assetPath", e)
        }
    }
    @JvmSynthetic
    @WorkerThread
    @Throws(IOException::class, NullPointerException::class)
    override fun disableNetwork(disable: Boolean) {
        onionProxyManager.disableNetwork(disable)
    }
    @JvmSynthetic
    override fun getBroadcastLogger(clazz: Class<*>): BroadcastLogger {
        return onionProxyManager.getBroadcastLogger(clazz)
    }
    @JvmSynthetic
    override fun hasNetworkConnectivity(): Boolean {
        return onionProxyManager.hasNetworkConnectivity()
    }
    @JvmSynthetic
    override fun hasControlConnection(): Boolean {
        return onionProxyManager.hasControlConnection
    }
    @JvmSynthetic
    override fun isTorOff(): Boolean {
        return onionProxyManager.torStateMachine.isOff
    }
    @JvmSynthetic
    override fun isTorOn(): Boolean {
        return onionProxyManager.torStateMachine.isOn
    }
    @JvmSynthetic
    override fun refreshBroadcastLoggersHasDebugLogsVar() {
        onionProxyManager.refreshBroadcastLoggersHasDebugLogsVar()
    }
    @JvmSynthetic
    @WorkerThread
    override fun signalControlConnection(torControlCommand: String): Boolean {
        return onionProxyManager.signalControlConnection(torControlCommand)
    }
    @JvmSynthetic
    @WorkerThread
    override suspend fun signalNewNym() {
        onionProxyManager.signalNewNym()
    }
    @JvmSynthetic
    @WorkerThread
    @Suppress("BlockingMethodInNonBlockingContext")
    override suspend fun startTor() {
        try {
            TorServiceController.serviceExecutionHooks
                ?.executeBeforeStartTor(getContext().applicationContext)

            onionProxyManager.setup()
            generateTorrcFile()

            onionProxyManager.start()
            delay(300L)
        } catch (e: Exception) {
            broadcastLogger.exception(e)
        }
    }
    @JvmSynthetic
    @WorkerThread
    @Suppress("BlockingMethodInNonBlockingContext")
    override suspend fun stopTor() {

        try {
            onionProxyManager.stop()
        } catch (e: Exception) {
            broadcastLogger.exception(e)
        }

        delay(300L)

        try {
            TorServiceController.serviceExecutionHooks
                ?.executeAfterStopTor(getContext().applicationContext)
        } catch (e: Exception) {
            broadcastLogger.exception(e)
        }
    }
    @WorkerThread
    @Throws(
        SecurityException::class,
        IllegalAccessException::class,
        IllegalArgumentException::class,
        InvocationTargetException::class,
        NullPointerException::class,
        ExceptionInInitializerError::class,
        IOException::class
    )
    private fun generateTorrcFile() {
        onionProxyManager.getNewSettingsBuilder()
            .updateTorSettings()
            .setGeoIpFiles()
            .setV3AuthPrivateDir()
            .finishAndWriteToTorrcFile()
    }


    ///////////////////////////////
    /// TorServicePrefsListener ///
    ///////////////////////////////

    // See BaseService


    override fun onCreate() {
        broadcastLogger.notice("BuildConfig.DEBUG set to: ${getBuildConfigDebug()}")
        super.onCreate()
    }

    override fun onDestroy() {
        super.onDestroy()
        supervisorJob.cancel()
        removeNotification()
        BackgroundManager.killAppProcess()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Cancel the BackgroundManager's coroutine if it's active so it doesn't execute
        torServiceBinder.cancelExecuteBackgroundPolicyJob()
        super.onTaskRemoved(rootIntent)
    }
}