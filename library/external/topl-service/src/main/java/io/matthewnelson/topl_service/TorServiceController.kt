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
package io.matthewnelson.topl_service

import android.app.Application
import android.content.Context
import io.matthewnelson.topl_service.notification.ServiceNotification
import io.matthewnelson.topl_service.service.TorService
import io.matthewnelson.topl_core_base.EventBroadcaster
import io.matthewnelson.topl_core_base.TorConfigFiles
import io.matthewnelson.topl_core_base.TorSettings
import io.matthewnelson.topl_service.service.BaseService
import io.matthewnelson.topl_service.lifecycle.BackgroundManager
import io.matthewnelson.topl_service.service.components.actions.ServiceActionProcessor
import io.matthewnelson.topl_service.service.components.actions.ServiceAction
import io.matthewnelson.topl_service.service.components.binding.TorServiceConnection
import io.matthewnelson.topl_service.service.components.onionproxy.ServiceTorSettings
import io.matthewnelson.topl_service.util.ServiceConsts
import io.matthewnelson.topl_service.util.V3ClientAuthManager
import io.matthewnelson.topl_service_base.*

class TorServiceController private constructor(): ServiceConsts() {

    /**
     * The [TorServiceController.Builder] is where you get to customize how [TorService] works
     * for your application. Call it in `Application.onCreate` and follow along.
     *
     * @param [application] [Application], for obtaining context
     * @param [torServiceNotificationBuilder] The [ServiceNotification.Builder] for
     *   customizing [TorService]'s notification
     * @param [backgroundManagerPolicy] The [BackgroundManager.Builder.Policy] to be executed
     *   while your application is in the background (the Recent App's tray).
     * @param [buildConfigVersionCode] send [BuildConfig.VERSION_CODE]. Mitigates copying of geoip
     *   files to app updates only
     * @param [defaultTorSettings] [ApplicationDefaultTorSettings] used to create your torrc file
     *   on start of Tor
     * @param [geoipAssetPath] The path to where you have your geoip file located (ex: in
     *   assets/common directory, send this variable "common/geoip")
     * @param [geoip6AssetPath] The path to where you have your geoip6 file located (ex: in
     *   assets/common directory, send this variable "common/geoip6")
     *
     * @sample [io.matthewnelson.sampleapp.topl_android.CodeSamples.generateTorServiceNotificationBuilder]
     * @sample [io.matthewnelson.sampleapp.topl_android.CodeSamples.generateBackgroundManagerPolicy]
     * @sample [io.matthewnelson.sampleapp.topl_android.CodeSamples.setupTorServices]
     * */
    class Builder(
        private val application: Application,
        private val torServiceNotificationBuilder: ServiceNotification.Builder,
        private val backgroundManagerPolicy: BackgroundManager.Builder.Policy,
        private val buildConfigVersionCode: Int,
        private val defaultTorSettings: ApplicationDefaultTorSettings,
        private val geoipAssetPath: String,
        private val geoip6AssetPath: String
    ) {

//        private var heartbeatTime = BackgroundManager.heartbeatTime
        private var disableNetworkDelay = ServiceActionProcessor.getDisableNetworkDelay()
        private var restartTorDelayTime = ServiceActionProcessor.getRestartTorDelayTime()
        private var stopServiceDelayTime = ServiceActionProcessor.getStopServiceDelayTime()
        private var stopServiceOnTaskRemoved = true
        private var torConfigFiles: TorConfigFiles? = null

        // On published releases of this Library, this value will **always** be `false`.
        private var buildConfigDebug = BaseService.getBuildConfigDebug()

        /**
         * Default is set to 6_000ms, (what this method adds time to).
         *
         * When network connectivity is lost a delay is had before setting Tor's config
         * "DisableNetwork" to true, in case connectivity is re-gained within the delay period.
         *
         * Tor is not stopped on connectivity change, but the network setting will be disabled
         * to inhibit continual launching and building of circuits (which drains the battery).
         *
         * This delay is particularly useful when setting ports to "auto" because disabling
         * Tor's network and then re-enabling it (when connectivity is regained) will set up
         * listeners on different ports.
         *
         * The delay also helps in stabilizing longer running network calls amidst areas where
         * the User may have bad service. The calls will simply continue if connectivity is
         * regained within the delay period and ports will not be cycled (if using "auto").
         *
         * @param [milliseconds] A value greater than 0
         * @see [io.matthewnelson.topl_service.service.components.actions.ServiceAction.SetDisableNetwork]
         * @see [io.matthewnelson.topl_service.service.components.actions.ServiceActionProcessor.processServiceAction]
         * */
        fun addTimeToDisableNetworkDelay(milliseconds: Long): Builder {
            if (milliseconds > 0) {
                disableNetworkDelay += milliseconds
            }
            return this
        }

        /**
         * Default is set to 500ms, (what this method adds time to).
         *
         * A slight delay is required when starting and stopping Tor to allow the [Process]
         * for which it is running in to settle. This method adds time to the cautionary
         * delay between execution of stopTor and startTor, which are the individual calls
         * executed when using the [restartTor] method.
         *
         * The call to [restartTor] executes individual commands to:
         *
         *   - stop tor + delay (300ms)
         *   - delay (500ms) <---------------------- what this method will add to
         *   - start tor + delay (300ms)
         *
         * @param [milliseconds] A value greater than 0
         * @see [io.matthewnelson.topl_service.service.components.actions.ServiceAction.RestartTor]
         * @see [io.matthewnelson.topl_service.service.components.actions.ServiceActionProcessor.processServiceAction]
         * */
        fun addTimeToRestartTorDelay(milliseconds: Long): Builder {
            if (milliseconds > 0L) {
                this.restartTorDelayTime += milliseconds
            }
            return this
        }

        /**
         * Default is set to 100ms (what this method adds time to).
         *
         * A slight delay is required when starting and stopping Tor to allow the [Process]
         * for which it is running in to settle. This method adds time to the cautionary
         * delay between execution of stopping Tor and stopping [TorService].
         *
         * The call to [stopTor] executes individual commands to:
         *
         *   - stop tor + delay (300ms)
         *   - delay (100ms) <---------------------- what this method will add to
         *   - stop service
         *
         * @param [milliseconds] A value greater than 0
         * @see [io.matthewnelson.topl_service.service.components.actions.ServiceAction.Stop]
         * @see [io.matthewnelson.topl_service.service.components.actions.ServiceActionProcessor.processServiceAction]
         * */
        fun addTimeToStopServiceDelay(milliseconds: Long): Builder {
            if (milliseconds > 0L) {
                this.stopServiceDelayTime += milliseconds
            }
            return this
        }

        /**
         * When your task is removed from the Recent App's tray, [TorService.onTaskRemoved] is
         * triggered. Default behaviour is to stop Tor, and then [TorService]. Electing this
         * option will inhibit the default behaviour from being carried out.
         * */
        @JvmOverloads
        fun disableStopServiceOnTaskRemoved(disable: Boolean = true): Builder {
            stopServiceOnTaskRemoved = !disable
            return this
        }

        /**
         * This makes it such that on your Application's **Debug** builds, the `topl-core` and
         * `topl-service` modules will provide you with Logcat messages (when
         * [TorSettings.hasDebugLogs] is enabled).
         *
         * For your **Release** builds no Logcat messaging will be provided, but you
         * will still get the same messages sent to your [EventBroadcaster] if you set it
         * via [Builder.setEventBroadcaster].
         *
         * @param [buildConfigDebug] Send [BuildConfig.DEBUG]
         * @see [io.matthewnelson.topl_core.broadcaster.BroadcastLogger]
         * */
        fun setBuildConfigDebug(buildConfigDebug: Boolean): Builder {
            this.buildConfigDebug = buildConfigDebug
            return this
        }

        /**
         * Get broadcasts piped to your Application to do with them what you desire. What
         * you send this will live at [Companion.appEventBroadcaster] for the remainder of
         * your application's lifecycle to refer to elsewhere in your App.
         *
         * NOTE: You will, ofc, have to cast [Companion.appEventBroadcaster] as whatever your
         * class actually is.
         * */
        fun setEventBroadcaster(eventBroadcaster: TorServiceEventBroadcaster): Builder {
            if (appEventBroadcaster == null) {
                appEventBroadcaster = eventBroadcaster
            }
            return this
        }

        /**
         * Implement and set hooks to be executed in [TorService.onCreate], and
         * [ServiceActionProcessor.processServiceAction] prior to starting of Tor, and
         * post stopping of Tor.
         * */
        fun setServiceExecutionHooks(executionHooks: ServiceExecutionHooks): Builder {
            if (serviceExecutionHooks == null) {
                serviceExecutionHooks = executionHooks
            }
            return this
        }

        /**
         * If you wish to customize the file structure of how Tor is installed in your app,
         * you can do so by instantiating your own [TorConfigFiles] and customizing it via
         * the [TorConfigFiles.Builder], or overridden method [TorConfigFiles.createConfig].
         *
         * By default, [TorService] will call [TorConfigFiles.createConfig] using your
         * [Context.getApplicationContext] to set up a standard directory hierarchy for Tor
         * to operate with.
         *
         * @return [Builder]
         * @sample [io.matthewnelson.sampleapp.topl_android.CodeSamples.customTorConfigFilesSetup]
         * @see [Builder.build]
         * */
        fun useCustomTorConfigFiles(torConfigFiles: TorConfigFiles): Builder {
            this.torConfigFiles = torConfigFiles
            return this
        }

        /**
         * Initializes [TorService] setup and enables the ability to call methods from the
         * [Companion] object w/o throwing exceptions.
         *
         * See [Builder] for code samples.
         *
         * @throws [IllegalArgumentException] If [disableStopServiceOnTaskRemoved] was elected
         *   and your selected [BackgroundManager.Builder.Policy] is **not**
         *   [io.matthewnelson.topl_service_base.BaseServiceConsts.BackgroundPolicy.RUN_IN_FOREGROUND]
         *   and/or [BackgroundManager.Builder.killAppIfTaskIsRemoved] is **not** `true`
         * */
        fun build() {

            // Must be called before application gets set
            ServiceActionProcessor.initialize(
                disableNetworkDelay, restartTorDelayTime, stopServiceDelayTime
            )

            BaseService.initialize(
                application,
                buildConfigVersionCode,
                defaultTorSettings,
                buildConfigDebug,
                geoipAssetPath,
                geoip6AssetPath,
                torConfigFiles ?: TorConfigFiles.createConfig(application.applicationContext),
                stopServiceOnTaskRemoved
            )

//            BackgroundManager.initialize(heartbeatTime)
            torServiceNotificationBuilder.build(application.applicationContext)

            if (backgroundManagerPolicy.configurationIsCompliant(stopServiceOnTaskRemoved)) {
                backgroundManagerPolicy.build()
            } else {
                throw IllegalArgumentException(
                    "disableStopServiceOnTaskRemoved requires a BackgroundManager Policy of " +
                            "${BackgroundPolicy.RUN_IN_FOREGROUND}, and " +
                            "killAppIfTaskIsRemoved must be set to true."
                )
            }
        }
    }

    /**
     * Where everything needed to interact with [TorService] resides.
     * */
    companion object {

        @JvmStatic
        var appEventBroadcaster: TorServiceEventBroadcaster? = null
            private set

        @JvmStatic
        var serviceExecutionHooks: ServiceExecutionHooks? = null
            private set

        /**
         * Get the [TorConfigFiles] that have been set after calling [Builder.build]
         *
         * This method will *never* throw the [RuntimeException] if you call it after
         * [Builder.build].
         *
         * @return Instance of [TorConfigFiles] that are being used throughout TOPL-Android
         * @throws [RuntimeException] if called before [Builder.build]
         * */
        @JvmStatic
        @Throws(RuntimeException::class)
        fun getTorConfigFiles(): TorConfigFiles {
            return try {
                BaseService.getTorConfigFiles()
            } catch (e: UninitializedPropertyAccessException) {
                throw RuntimeException(e.message)
            }
        }

        /**
         * This method will *never* throw the [RuntimeException] if you call it after
         * [Builder.build].
         *
         * @return Instance of [ApplicationDefaultTorSettings] that were used to instantiate
         *   [TorServiceController.Builder] with
         * @throws [RuntimeException] if called before [Builder.build]
         * */
        @JvmStatic
        @Throws(RuntimeException::class)
        fun getDefaultTorSettings(): ApplicationDefaultTorSettings {
            return try {
                BaseService.getApplicationDefaultTorSettings()
            } catch (e: UninitializedPropertyAccessException) {
                throw RuntimeException(e.message)
            }
        }

        /**
         * This method will *never* throw the [RuntimeException] if you call it after
         * [Builder.build].
         *
         * @return The implemented [BaseV3ClientAuthManager] for adding, querying, and removing
         *   v3 Client Authorization private key files
         * @throws [RuntimeException] if called before [Builder.build]
         * */
        @JvmStatic
        @Throws(RuntimeException::class)
        fun getV3ClientAuthManager(): BaseV3ClientAuthManager =
            V3ClientAuthManager.instantiate(getTorConfigFiles())

        /**
         * This method will *never* throw the [RuntimeException] if you call it after
         * [Builder.build].
         *
         * @return [BaseServiceTorSettings]
         * @throws [RuntimeException] if called before [Builder.build]
         * */
        @JvmStatic
        @Throws(RuntimeException::class)
        fun getServiceTorSettings(): BaseServiceTorSettings =
            ServiceTorSettings.instantiate(
                TorServicePrefs(BaseService.getAppContext()), getDefaultTorSettings()
            )

        /**
         * This method will *never* throw the [RuntimeException] if you call it after
         * [Builder.build].
         *
         * Starts [TorService] and then Tor. You can call this as much as you want. If
         * the Tor [Process] is already running, it will do nothing.
         *
         * @throws [RuntimeException] if called before [Builder.build]
         * */
        @JvmStatic
        @Throws(RuntimeException::class)
        fun startTor() {
            BaseService.startService(BaseService.getAppContext(), TorService::class.java)
        }

        /**
         * Stops [TorService].
         * */
        @JvmStatic
        fun stopTor() {
            TorServiceConnection.getServiceBinder()?.submitServiceAction(ServiceAction.Stop.instantiate())
        }

        /**
         * Restarts Tor.
         * */
        @JvmStatic
        fun restartTor() {
            TorServiceConnection.getServiceBinder()?.submitServiceAction(ServiceAction.RestartTor.instantiate())
        }

        /**
         * Changes identities.
         * */
        @JvmStatic
        fun newIdentity() {
            TorServiceConnection.getServiceBinder()?.submitServiceAction(ServiceAction.NewId.instantiate())
        }
    }
}