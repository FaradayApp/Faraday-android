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
package io.matthewnelson.topl_service.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.NotificationVisibility
import androidx.core.content.ContextCompat
import io.matthewnelson.topl_service.R
import io.matthewnelson.topl_service.notification.ServiceNotification.Builder
import io.matthewnelson.topl_service.service.BaseService
import io.matthewnelson.topl_service.service.TorService
import io.matthewnelson.topl_service.service.components.receiver.TorServiceReceiver
import io.matthewnelson.topl_service.util.ServiceConsts
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Everything to do with [TorService]'s notification.
 *
 * @see [Builder]
 * */
class ServiceNotification private constructor(
    private val channelName: String,
    private val channelID: String,
    private val channelDescription: String,
    private val notificationID: Int,

    var activityWhenTapped: Class<*>? = null,
    var activityIntentKey: String? = null,
    var activityIntentExtras: String? = null,
    var activityIntentBundle: Bundle? = null,
    var activityIntentRequestCode: Int = 0,
    var contentPendingIntent: PendingIntent? = null,

    @DrawableRes var imageNetworkEnabled: Int = R.drawable.tor_stat_network_enabled,
    @DrawableRes var imageNetworkDisabled: Int = R.drawable.tor_stat_network_disabled,
    @DrawableRes var imageDataTransfer: Int = R.drawable.tor_stat_network_dataxfer,
    @DrawableRes var imageError: Int = R.drawable.tor_stat_notifyerr,

    @ColorRes var colorWhenConnected: Int = R.color.tor_service_white,

    @NotificationVisibility var visibility: Int = NotificationCompat.VISIBILITY_SECRET,

    var enableRestartButton: Boolean = false,
    var enableStopButton: Boolean = false,

    var showNotification: Boolean = true
): ServiceConsts() {


    ///////////////
    /// Builder ///
    ///////////////
    /**
     * Where you get to customize how your notification will look and function.
     *
     * A notification is required to be displayed while [TorService] is running in the
     * Foreground. Even if you set [Builder.showNotification] to false, [TorService]
     * is brought to the Foreground when the user removes your task from the recent apps tray
     * in order to properly shut down Tor and clean up w/o being killed by the OS.
     *
     * @param [channelName] Your notification channel's name (Cannot be Empty).
     * @param [channelID] Your notification channel's ID (Cannot be Empty).
     * @param [channelDescription] Your notification channel's description (Cannot be Empty).
     * @param [notificationID] Your foreground notification's ID.
     * @sample [io.matthewnelson.sampleapp.topl_android.CodeSamples.generateTorServiceNotificationBuilder]
     * @throws [IllegalArgumentException] If String fields are empty.
     * */
    class Builder(
        channelName: String,
        channelID: String,
        channelDescription: String,
        notificationID: Int
    ) {

        init {
            require(
                channelName.isNotEmpty() && channelID.isNotEmpty() && channelDescription.isNotEmpty()
            ) { "channelName, channelID, & channelDescription must not be empty." }
        }

        private val serviceNotification = ServiceNotification(
            channelName,
            channelID,
            channelDescription,
            notificationID
        )

        /**
         * Do not use this method.
         *
         * @see [setContentIntent]
         *
         * @param [clazz] The Activity to be opened when tapped.
         * @param [intentExtrasKey]? The key for if you with to add extras in the PendingIntent.
         * @param [intentExtras]? The extras that will be sent in the PendingIntent.
         * @param [intentRequestCode]? The request code - Defaults to 0 if not set.
         * */
        @Deprecated(
            message = "This method will be removed in a future release",
            replaceWith = ReplaceWith("setContentIntent(pendingIntent = null)")
        )
        fun setActivityToBeOpenedOnTap(
            clazz: Class<*>,
            intentExtrasKey: String?,
            intentExtras: String?,
            intentRequestCode: Int?
        ): Builder {
            this.serviceNotification.activityWhenTapped = clazz
            this.serviceNotification.activityIntentKey = intentExtrasKey
            this.serviceNotification.activityIntentExtras = intentExtras
            intentRequestCode?.let { serviceNotification.activityIntentRequestCode = it }
            return this
        }


        /**
         * Do not use this method.
         *
         * A non-null, non-0 number must be supplied for requestCode.
         *
         * @see [setContentIntent]
         *
         * @param [bundle] Bundle to be sent to the Launch Activity
         * @param [requestCode] Request Code to be used when launching the Activity
         * */
        @Deprecated(
            message = "This method will be removed in a future release",
            replaceWith = ReplaceWith("setContentIntent(pendingIntent = null)")
        )
        fun setContentIntentData(
            bundle: Bundle?,
            requestCode: Int?
        ): Builder {
            this.serviceNotification.activityIntentBundle = bundle
            requestCode?.let { serviceNotification.activityIntentRequestCode = it }
            return this
        }

        /**
         * Allows for full control over the [PendingIntent] used when the user taps the
         * [ServiceNotification].
         *
         * **NOTE**: use applicationContext when building your pending intent.
         * */
        fun setContentIntent(pendingIntent: PendingIntent?): Builder {
            this.serviceNotification.contentPendingIntent = pendingIntent
            return this
        }

        /**
         * Defaults to Orbot/TorBrowser's icon [R.drawable.tor_stat_network_enabled].
         *
         * The small icon you wish to display when Tor's network state is
         * [io.matthewnelson.topl_core_base.BaseConsts.TorNetworkState.ENABLED].
         *
         * See [Builder] for code samples.
         *
         * @param [drawableRes] Drawable resource id
         * @return [Builder] To continue customizing
         * */
        fun setImageTorNetworkingEnabled(@DrawableRes drawableRes: Int): Builder {
            this.serviceNotification.imageNetworkEnabled = drawableRes
            return this
        }

        /**
         * Defaults to Orbot/TorBrowser's icon [R.drawable.tor_stat_network_disabled].
         *
         * The small icon you wish to display when Tor's network state is
         * [io.matthewnelson.topl_core_base.BaseConsts.TorNetworkState.DISABLED].
         *
         * See [Builder] for code samples.
         *
         * @param [drawableRes] Drawable resource id
         * @return [Builder] To continue customizing
         * */
        fun setImageTorNetworkingDisabled(@DrawableRes drawableRes: Int): Builder {
            this.serviceNotification.imageNetworkDisabled = drawableRes
            return this
        }

        /**
         * Defaults to Orbot/TorBrowser's icon [R.drawable.tor_stat_network_dataxfer].
         *
         * The small icon you wish to display when bandwidth is being used.
         *
         * See [Builder] for code samples.
         *
         * @param [drawableRes] Drawable resource id
         * @return [Builder] To continue customizing
         * */
        fun setImageTorDataTransfer(@DrawableRes drawableRes: Int): Builder {
            this.serviceNotification.imageDataTransfer = drawableRes
            return this
        }

        /**
         * Defaults to Orbot/TorBrowser's icon [R.drawable.tor_stat_notifyerr].
         *
         * The small icon you wish to display when Tor is having problems.
         *
         * See [Builder] for code samples.
         *
         * @param [drawableRes] Drawable resource id
         * @return [Builder] To continue customizing
         * */
        fun setImageTorErrors(@DrawableRes drawableRes: Int): Builder {
            this.serviceNotification.imageError = drawableRes
            return this
        }

        /**
         * Defaults to [R.color.tor_service_white]
         *
         * The color you wish to display when Tor's network state is
         * [io.matthewnelson.topl_core_base.BaseConsts.TorNetworkState.ENABLED].
         *
         * See [Builder] for code samples.
         *
         * @param [colorRes] Color resource id
         * @return [Builder] To continue customizing
         * */
        fun setCustomColor(@ColorRes colorRes: Int): Builder {
            this.serviceNotification.colorWhenConnected = colorRes
            return this
        }

        /**
         * Defaults to NotificationVisibility.VISIBILITY_SECRET
         *
         * The visibility of your notification on the user's lock screen.
         *
         * See [Builder] for code samples.
         *
         * @param [visibility] The [NotificationVisibility] you desire your notification to have
         * @return [Builder] To continue customizing
         * */
        fun setVisibility(@NotificationVisibility visibility: Int): Builder {
            if (visibility in -1..1) {
                this.serviceNotification.visibility = visibility
            }
            return this
        }

        /**
         * Disabled by Default
         *
         * Enable on the notification the ability to **restart** Tor.
         *
         * See [Builder] for code samples.
         *
         * @param [enable] Boolean, automatically set to true but provides cleaner option
         *   for implementor to query SharedPreferences for user's settings (if desired)
         * @return [Builder] To continue customizing
         * */
        @JvmOverloads
        fun enableTorRestartButton(enable: Boolean = true): Builder {
            this.serviceNotification.enableRestartButton = enable
            return this
        }

        /**
         * Disabled by Default
         *
         * Enable on the notification the ability to **stop** Tor.
         *
         * See [Builder] for code samples.
         *
         * @param [enable] Boolean, automatically set to true but provides cleaner option
         *   for implementor to query SharedPreferences for user's settings (if desired)
         * @return [Builder] To continue customizing
         * */
        @JvmOverloads
        fun enableTorStopButton(enable: Boolean = true): Builder {
            this.serviceNotification.enableStopButton = enable
            return this
        }

        /**
         * Shown by Default.
         *
         * Setting it to false will only show a notification when the end user removes your
         * Application from the Recent App's tray. In that event, [TorService.onTaskRemoved]
         * moves the Service to the Foreground in order to properly shutdown Tor w/o the OS
         * killing it beforehand.
         *
         * See [Builder] for code samples.
         *
         * @param [show] Boolean, automatically set to false but provides cleaner option for
         *   implementor to query SharedPreferences for user's settings (if desired)
         * @return [Builder] To continue customizing
         * */
        @JvmOverloads
        fun showNotification(show: Boolean = false): Builder {
            this.serviceNotification.showNotification = show
            return this
        }

        /**
         * Initializes your notification customizations and sets up the notification
         * channel. This is called by
         * [io.matthewnelson.topl_service.TorServiceController.Builder.build]
         * */
        @JvmSynthetic
        internal fun build(context: Context) {
            // Only initialize it once. Reflection has issues here
            // as it's in a Companion object.
            try {
                Companion.serviceNotification.hashCode()
            } catch (e: UninitializedPropertyAccessException) {
                Companion.serviceNotification = this.serviceNotification
                Companion.serviceNotification.setupNotificationChannel(context)
            }
        }

    }

    companion object {
        private lateinit var serviceNotification: ServiceNotification

        @JvmSynthetic
        @Throws(UninitializedPropertyAccessException::class)
        internal fun getServiceNotification(): ServiceNotification =
            serviceNotification
    }


    /////////////
    /// Setup ///
    /////////////
    private var notificationBuilder: NotificationCompat.Builder? = null
    private var notificationManager: NotificationManager? = null
    private val timeoutLength = 3_000L
    private var startTime: Long? = null

    @JvmSynthetic
    internal fun buildNotification(
        torService: BaseService,
        setStartTime: Boolean = false
    ): NotificationCompat.Builder {
        val builder = NotificationCompat.Builder(torService.getContext().applicationContext, channelID)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentText(currentContentText)
            .setContentTitle(currentContentTitle)
            .setGroup("TorService")
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
            .setGroupSummary(false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSmallIcon(currentIcon)
            .setSound(null)
            .setTimeoutAfter(timeoutLength)
            .setVisibility(visibility)

        if (startTime == null || setStartTime)
            startTime = System.currentTimeMillis()

        startTime?.let {
            builder.setWhen(it)
        }

        currentColor?.let {
            builder.color = it
        }

        if (progressBarShown) {
            val progress = progressValue
            if (progress != null)
                builder.setProgress(100, progress, false)
            else
                builder.setProgress(100, 0, true)
        }

        contentPendingIntent?.let { pendingIntent ->
            builder.setContentIntent(pendingIntent)
        } ?: activityWhenTapped?.let { clazz ->
            builder.setContentIntent(getContentPendingIntent(torService, clazz))

            // if the request code has been changed
        } ?: if (activityIntentRequestCode != 0) {
                torService.getContext().packageManager
                    ?.getLaunchIntentForPackage(torService.getContext().packageName)
                    ?.let { intent ->
                        builder.setContentIntent(
                            PendingIntent.getActivity(
                                torService.getContext(),
                                activityIntentRequestCode,
                                intent,
                                PendingIntent.FLAG_UPDATE_CURRENT,
                                activityIntentBundle
                            )
                        )
                    }
            } else {

        }

        notificationBuilder = builder
        return builder
    }

    private fun getContentPendingIntent(torService: BaseService, clazz: Class<*>): PendingIntent {
        val contentIntent = Intent(torService.getContext(), clazz)

        if (!activityIntentKey.isNullOrEmpty() && !activityIntentExtras.isNullOrEmpty())
            contentIntent.putExtra(activityIntentKey, activityIntentExtras)

        return PendingIntent.getActivity(
            torService.getContext(),
            activityIntentRequestCode,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun notify(torService: BaseService, builder: NotificationCompat.Builder) {
        notificationBuilder = builder
        if (showNotification || inForeground) {
            launchRefreshNotificationJob(torService)
            notificationManager?.notify(notificationID, builder.build())
        }
    }

    private var notificationRefreshJob: Job? = null

    /**
     * The only way to keep the service out of the Foreground as to not interrupt the
     * Application's lifecycle (b/c of horrible APIs for the Service class), is to make the
     * notification timeout after a certain number of milliseconds. This coroutine paired
     * with [notify] is recursive in nature such that it will be canceled then re-launched
     * every time [notify] is called as to keep the `setTimeoutAfter` value from actually
     * timing out. This is necessary, as if the service is killed by the OS the notification
     * will simply cancel itself b/c this coroutine will not be active to keep it refreshed. When
     * [TorService] is destroyed, the supervisorJob is cancelled which cancels this
     * coroutine as well, ending the recursion.
     * */
    private fun launchRefreshNotificationJob(torService: BaseService) {
        if (notificationRefreshJob?.isActive == true)
            notificationRefreshJob?.cancel()

        if (inForeground)
            return

        notificationRefreshJob = torService.getScopeIO().launch {
            delay(timeoutLength - 250L)
            if (!inForeground)
                notificationBuilder?.let {
                    notify(torService, it)
                }
        }
    }

    @JvmSynthetic
    @Synchronized
    internal fun remove() {
        notificationManager?.cancel(notificationID)
    }

    /**
     * Called once per application start in
     * [io.matthewnelson.topl_service.TorServiceController.Builder.build]
     * */
    @JvmSynthetic
    internal fun setupNotificationChannel(context: Context): ServiceNotification {
        notificationManager = context.applicationContext
            .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelID,
                channelName,
                NotificationManager.IMPORTANCE_DEFAULT
            )
            channel.description = channelDescription
            channel.setSound(null, null)
            notificationManager?.createNotificationChannel(channel)
        }
        return serviceNotification
    }


    //////////////////////////
    /// Foreground Service ///
    //////////////////////////
    @Volatile
    private var inForeground = false

    /**
     * Sends [TorService] to the Foreground.
     *
     * @return `true` if sent to Foreground, `false` if no action taken
     * */
    @JvmSynthetic
    @Synchronized
    internal fun startForeground(torService: BaseService): Boolean {
        return if (!inForeground) {
            notificationBuilder?.let {
                torService.startForeground(notificationID, it.build())
                inForeground = true
                return true
            }
            false
        } else {
            false
        }
    }

    /**
     * Sends [TorService] to the Background.
     *
     * @return `true` if sent to Background, `false` if no action taken
     * */
    @Suppress("DEPRECATION")
    @JvmSynthetic
    @Synchronized
    internal fun stopForeground(torService: BaseService): Boolean {
        return if (inForeground) {
            torService.stopForeground(!showNotification)
            inForeground = false
            notificationBuilder?.let {
                notify(torService, it)
            }
            true
        } else {
            false
        }
    }


    ///////////////
    /// Actions ///
    ///////////////
    @Volatile
    private var actionsPresent = false

    @JvmSynthetic
    @Synchronized
    internal fun addActions(torService: BaseService) {
        val builder = notificationBuilder ?: return
        actionsPresent = true

        builder.addAction(
            0,
            "New Identity",
            getActionPendingIntent(torService, ServiceActionName.NEW_ID, 1)
        )

        if (enableRestartButton && TorServiceReceiver.deviceIsLocked() != true)
            builder.addAction(
                0,
                "Restart Tor",
                getActionPendingIntent(torService, ServiceActionName.RESTART_TOR, 2)
            )

        if (enableStopButton && TorServiceReceiver.deviceIsLocked() != true)
            builder.addAction(
                0,
                "Stop Tor",
                getActionPendingIntent(torService, ServiceActionName.STOP, 3)
            )
        notify(torService, builder)
    }

    private fun getActionPendingIntent(
        torService: BaseService,
        @ServiceActionName action: String,
        requestCode: Int
    ): PendingIntent {
        val intent = Intent(TorServiceReceiver.getServiceIntentFilter())
        intent.putExtra(TorServiceReceiver.getServiceIntentFilter(), action)
        intent.setPackage(torService.getContext().packageName)

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        return PendingIntent.getBroadcast(
            torService.getContext(),
            requestCode,
            intent,
            flags
        )
    }

    /**
     * Refreshes the notification Actions.
     *
     * @return `true` if actions were present to be refreshed, `false` if actions weren't
     *   present, thus not needing a refresh
     * @see [TorServiceReceiver.deviceIsLocked]
     * */
    @JvmSynthetic
    @Synchronized
    internal fun refreshActions(torService: BaseService): Boolean {
        return if (actionsPresent) {
            removeActions(torService)
            addActions(torService)
            true
        } else {
            false
        }
    }

    @JvmSynthetic
    @Synchronized
    internal fun removeActions(torService: BaseService) {
        actionsPresent = false
        notify(torService, buildNotification(torService))
    }


    ////////////////////
    /// Content Text ///
    ////////////////////
    @Volatile
    private var currentContentText = "Starting Tor..."
    @JvmSynthetic
    internal fun getCurrentContentText(): String =
        currentContentText

    @JvmSynthetic
    @Synchronized
    internal fun updateContentText(torService: BaseService, string: String) {
        if (currentContentText == string) return
        currentContentText = string
        val builder = notificationBuilder ?: return
        builder.setContentText(string)
        notify(torService, builder)
    }


    /////////////////////
    /// Content Title ///
    /////////////////////
    @Volatile
    private var currentContentTitle = TorState.OFF
    @JvmSynthetic
    internal fun getCurrentContentTitle(): @TorState String =
        currentContentTitle

    @JvmSynthetic
    @Synchronized
    internal fun updateContentTitle(torService: BaseService, title: String) {
        if (currentContentTitle == title) return
        currentContentTitle = title
        val builder = notificationBuilder ?: return
        builder.setContentTitle(title)
        notify(torService, builder)
    }


    ////////////
    /// Icon ///
    ////////////
    @Volatile
    private var currentIcon = imageNetworkDisabled
    @JvmSynthetic
    internal fun getCurrentIcon(): Int =
        currentIcon

    @Volatile
    private var currentColor: Int? = null

    @JvmSynthetic
    @Synchronized
    internal fun updateIcon(torService: BaseService, @NotificationImage notificationImage: Int) {
        val builder = notificationBuilder ?: return
        when (notificationImage) {
            NotificationImage.ENABLED -> {
                if (currentIcon == imageNetworkEnabled) return
                currentIcon = imageNetworkEnabled
                builder.setSmallIcon(imageNetworkEnabled)

                val color = ContextCompat.getColor(torService.getContext(), colorWhenConnected)
                builder.color = color
                currentColor = color
            }
            NotificationImage.DISABLED -> {
                if (currentIcon == imageNetworkDisabled) return
                currentIcon = imageNetworkDisabled
                builder.setSmallIcon(imageNetworkDisabled)

                val color = ContextCompat.getColor(torService.getContext(), R.color.tor_service_white)
                builder.color = color
                currentColor = color
            }
            NotificationImage.DATA -> {
                if (currentIcon == imageDataTransfer) return
                currentIcon = imageDataTransfer
                builder.setSmallIcon(imageDataTransfer)
            }
            NotificationImage.ERROR -> {
                if (currentIcon == imageError) return
                currentIcon = imageError
                builder.setSmallIcon(imageError)
            }
            else -> {}
        }
        notify(torService, builder)
    }


    ////////////////////
    /// Progress Bar ///
    ////////////////////
    @Volatile
    private var progressBarShown = true
    @JvmSynthetic
    internal fun getProgressBarShown(): Boolean =
        progressBarShown

    @Volatile
    private var progressValue: Int? = null

    @JvmSynthetic
    @Synchronized
    internal fun updateProgress(torService: BaseService, show: Boolean, progress: Int? = null) {
        val builder = notificationBuilder ?: return
        progressBarShown = when {
            progress != null -> {
                builder.setProgress(100, progress, false)
                progressValue = progress
                true
            }
            show -> {
                builder.setProgress(100, 0, true)
                progressValue = null
                true
            }
            else -> {
                builder.setProgress(0, 0, false)
                progressValue = null
                false
            }
        }
        notify(torService, builder)
    }
}
