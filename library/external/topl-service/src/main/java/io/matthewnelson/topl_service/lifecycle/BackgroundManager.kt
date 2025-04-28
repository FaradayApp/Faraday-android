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
@file:Suppress("DEPRECATION")

package io.matthewnelson.topl_service.lifecycle

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ProcessLifecycleOwner
import io.matthewnelson.topl_service.service.BaseService
import io.matthewnelson.topl_service.service.TorService
import io.matthewnelson.topl_service.service.components.actions.ServiceActionProcessor
import io.matthewnelson.topl_service.service.components.binding.TorServiceConnection
import io.matthewnelson.topl_service.util.ServiceConsts
import io.matthewnelson.topl_service_base.BaseServiceConsts.BackgroundPolicy
import io.matthewnelson.topl_service_base.BaseServiceConsts.ServiceActionName
import kotlin.system.exitProcess

/**
 * When your application is sent to the background (the Recent App's tray or lock screen), the
 * chosen [BackgroundManager.Builder.Policy] will be triggered.
 *
 * Additionally, there are 2 values for you to query if needed to give you context surrounding
 * your application's background state; [taskIsInForeground] and [taskIsRemovedFromRecentApps].
 *
 * If brought back into the foreground by the user:
 *
 *   - **Before Policy execution**: Execution is canceled. If [BaseService.lastAcceptedServiceAction]
 *   was **not** [ServiceActionName.STOP], a startService call is made to ensure it's
 *   started.
 *
 *   - **After Policy execution**: If [BaseService.lastAcceptedServiceAction] was **not**
 *   [ServiceActionName.STOP], a startService call is made to ensure it's started.
 *
 *   - See [io.matthewnelson.topl_service.service.components.binding.BaseServiceBinder],
 *   [BaseService.updateLastAcceptedServiceAction], and [TorService.onTaskRemoved] for
 *   more information.
 *
 * While your application is in the foreground the only way to stop the service is by
 * calling [io.matthewnelson.topl_service.TorServiceController.stopTor], or via the
 * [io.matthewnelson.topl_service.notification.ServiceNotification] Action (if enabled);
 * The OS will not kill a service started using `Context.startService` &
 * `Context.bindService` (how [TorService] is started) while in the foreground.
 *
 * When the user sends your application to the Recent App's tray though, to recoup resources
 * the OS will kill your app after being idle for a period of time (random AF... typically
 * 0.75m to 1.25m if the device's memory is being used heavily) if the service is not moved to
 * the Foreground to inhibit this. This is not an issue if the user removes the task before the
 * OS kills the app, as Tor will be able to shutdown properly and the service will stop.
 *
 * This is where Services get sketchy (especially when trying to implement an always
 * running service for networking), and is the purpose of the [BackgroundManager] class.
 *
 * This class starts your chosen [BackgroundManager.Builder.Policy] as soon as your
 * application is sent to the background. It facilitates a more declarative, flexible
 * operation to fit Library users' needs.
 *
 * See the [BackgroundManager.Builder] for more detail.
 *
 * @param [policy] The chosen [BackgroundPolicy] to be executed.
 * @param [executionDelay] Length of time before the policy gets executed *after* the application
 *   is sent to the background.
 * @param [serviceClass] The Service class being managed
 * @param [bindServiceFlag] The flag to be used when binding to the service
 * @see [io.matthewnelson.topl_service.service.components.binding.BaseServiceBinder.executeBackgroundPolicyJob]
 * @see [io.matthewnelson.topl_service.service.components.binding.BaseServiceBinder.cancelExecuteBackgroundPolicyJob]
 * */
class BackgroundManager private constructor(
    @BackgroundPolicy private val policy: String,
    private val executionDelay: Long,
    private val serviceClass: Class<*>,
    private val bindServiceFlag: Int,
    private val killAppIfTaskIsRemoved: Boolean
): ServiceConsts(), LifecycleObserver {


    /**
     * This [BackgroundManager.Builder] sets how you want the service to operate while your
     * app is in the background (the Recent App's tray or lock screen), such that things run
     * reliably based off of your application's needs.
     *
     * When your application is brought back into the foreground your [Policy] is canceled
     * and, if [BaseService.lastAcceptedServiceAction] was **not** to Stop the service, a
     * startup command is issued to bring it back to the started state no matter if it is still
     * running or not.
     *
     * @sample [io.matthewnelson.sampleapp.topl_android.CodeSamples.generateBackgroundManagerPolicy]
     * */
    class Builder {

        @BackgroundPolicy
        private lateinit var chosenPolicy: String
        private var executionDelay: Long = 30_000L
        private var killAppIfTaskIsRemoved = false

        /**
         * Stops [TorService] after being in the background for the declared [secondsFrom5To45].
         *
         * Your application won't go through it's normal `Application.onCreate` process unless
         * it was killed, but [TorService] may have been stopped when the policy gets executed.
         *
         * Electing this option ensures [TorService] gets restarted in a more reliable manner then
         * returning `Context.START_STICKY` in [TorService.onStartCommand]. It also allows for
         * a proper shutdown of Tor prior to the service being stopped instead of it being
         * killed along with your application (which causes problems sometimes).
         *
         * If killed by the OS then this gets garbage collected such that in the event
         * the user brings your application back into the foreground (after it had been killed),
         * this will be re-instantiated when going through `Application.onCreate` again, and
         * [TorService] started by however you have it implemented.
         *
         * @param [secondsFrom5To45]? Seconds before the [Policy] is executed after the
         *   Application goes to the background. Sending null will use the default (30s)
         * @return [BackgroundManager.Builder.Policy] To use when initializing
         *   [io.matthewnelson.topl_service.TorServiceController.Builder]
         * */
        @JvmOverloads
        fun respectResourcesWhileInBackground(secondsFrom5To45: Int? = null): Policy {
            chosenPolicy = BackgroundPolicy.RESPECT_RESOURCES
            if (secondsFrom5To45 != null && secondsFrom5To45 in 5..45) {
                executionDelay = (secondsFrom5To45 * 1000).toLong()
            }
            return Policy.instantiate(this)
        }

        /**
         * Electing this option will, when your application is sent to the background, immediately
         * move [TorService] to the Foreground. If the user returns to your application,
         * [TorService] will then be backgrounded.
         *
         * Some things to note about your application's behaviour with this option:
         *
         *   - If the user sends your app to the recent App's tray and then swipes it away,
         *   [TorService.onTaskRemoved] will stop Tor, and then [TorService].
         *   - Because of how shitty the Service APIs are, your application will _not_ be
         *   killed like one would expect, thus not going through `Application.onCreate` if the
         *   user re-launches your application.
         *   - In the event of being re-launched in the aforementioned state,
         *   [applicationMovedToForeground] is called and Tor will be started again to match the
         *   Service's State for which it was left, prior to "terminating" your application.
         *   - Even while the Service has been properly stopped and everything cleaned up, your
         *   application will continue running and not be killed (Again, Service APIs...).
         *   - If [TorService] is stopped, and *then* your application is cleared from the
         *   recent apps tray, your application will be killed.
         *
         * @param [killAppIfTaskIsRemoved] If set to `true`, your Application's Process will be
         *   killed in [TorService.onDestroy] if the user removed the task from the recent app's
         *   tray and has not returned to the application before [killAppProcess] is called.
         * */
        @JvmOverloads
        fun runServiceInForeground(killAppIfTaskIsRemoved: Boolean = false): Policy {
            chosenPolicy = BackgroundPolicy.RUN_IN_FOREGROUND
            this.killAppIfTaskIsRemoved = killAppIfTaskIsRemoved
            return Policy.instantiate(this)
        }

        /**
         * Holds the chosen policy to be built in
         * [io.matthewnelson.topl_service.TorServiceController.Builder.build].
         *
         * @param [policyBuilder] The [BackgroundManager.Builder] to be built during initialization
         * */
        class Policy private constructor(private val policyBuilder: Builder) {

            companion object {
                @JvmSynthetic
                internal fun instantiate(policyBuilder: Builder): Policy =
                    Policy(policyBuilder)
            }

            @JvmSynthetic
            internal fun configurationIsCompliant(stopServiceOnTaskRemoved: Boolean): Boolean {
                return if (!stopServiceOnTaskRemoved) {
                    policyBuilder.chosenPolicy == BackgroundPolicy.RUN_IN_FOREGROUND &&
                            policyBuilder.killAppIfTaskIsRemoved
                } else {
                    true
                }
            }

            /**
             * Only available internally, so this is where we intercept for integration testing.
             * Calling [Policy.build] before
             * [io.matthewnelson.topl_service.TorServiceController.Builder.build] ensures our
             * test classes get initialized so they aren't overwritten by production classes.
             * */
            @JvmSynthetic
            internal fun build(
                serviceClass: Class<*> = TorService::class.java,
                bindServiceFlag: Int = Context.BIND_AUTO_CREATE
            ) {

                // Only initialize it once. Reflection has issues here
                // as it's in a Companion object.
                try {
                    backgroundManager.hashCode()
                } catch (e: UninitializedPropertyAccessException) {
                    backgroundManager =
                        BackgroundManager(
                            policyBuilder.chosenPolicy,
                            policyBuilder.executionDelay,
                            serviceClass,
                            bindServiceFlag,
                            policyBuilder.killAppIfTaskIsRemoved
                        )
                }
            }
        }
    }

    companion object {
        private lateinit var backgroundManager: BackgroundManager

        @JvmStatic
        @Volatile
        var taskIsInForeground = false
            private set

        @JvmStatic
        @Volatile
        var taskIsRemovedFromRecentApps = false
            private set

        @JvmSynthetic
        internal fun taskIsRemovedFromRecentApps(isRemoved: Boolean) {
            taskIsRemovedFromRecentApps = isRemoved
        }

        /**
         * Called from [TorService.onDestroy].
         *
         * Will only kill the application process if [taskIsRemovedFromRecentApps] is `true`.
         * [taskIsRemovedFromRecentApps] will turn back to `false` when
         * [applicationMovedToForeground] is fired off, as to not kill the application in the
         * event the user quickly re-launches the application between the time
         * [TorService.onTaskRemoved] gets a callback, and this method is called from
         * [TorService.onDestroy].
         * */
        @JvmSynthetic
        internal fun killAppProcess() {
            if (backgroundManager.killAppIfTaskIsRemoved && taskIsRemovedFromRecentApps) {
                exitProcess(0)
            }
        }
    }

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    private fun applicationMovedToForeground() {
        taskIsRemovedFromRecentApps(false)
        taskIsInForeground = true
        // if the last _accepted_ ServiceAction to be issued by the Application was not to STOP
        // the service, then we want to put it back in the state it was in
        if (!ServiceActionProcessor.wasLastAcceptedServiceActionStop()) {
            TorServiceConnection.getServiceBinder()?.cancelExecuteBackgroundPolicyJob()
            BaseService.startService(
                BaseService.getAppContext(),
                serviceClass,
                includeIntentActionStart = false,
                bindServiceFlag = bindServiceFlag
            )
        }
    }

    @Suppress("unused")
    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    private fun applicationMovedToBackground() {
        taskIsInForeground = false
        if (!ServiceActionProcessor.wasLastAcceptedServiceActionStop()) {
            TorServiceConnection.getServiceBinder()?.let { binder ->
                // System automatically unbinds when app is sent to the background. This prevents
                // it so that we maintain a started, bound service.
                BaseService.bindService(
                    BaseService.getAppContext(),
                    serviceClass,
                    bindServiceFlag = bindServiceFlag
                )
                binder.executeBackgroundPolicyJob(policy, executionDelay)
            }
        }
    }
}
