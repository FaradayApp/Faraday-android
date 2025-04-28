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
package io.matthewnelson.topl_service.util

import androidx.annotation.IntDef
import androidx.annotation.StringDef
import io.matthewnelson.topl_service_base.BaseServiceConsts

abstract class ServiceConsts: BaseServiceConsts() {


    //////////////////////////
    /// NotificationImages ///
    //////////////////////////
    @IntDef(
        NotificationImage.ENABLED,
        NotificationImage.DISABLED,
        NotificationImage.DATA,
        NotificationImage.ERROR
    )
    @Retention(AnnotationRetention.SOURCE)
    internal annotation class NotificationImage {
        companion object {
            const val ENABLED = 0
            const val DISABLED = 1
            const val DATA = 2
            const val ERROR = 3
        }
    }


    /////////////////////
    /// ServiceAction ///
    /////////////////////
    @Target(
        AnnotationTarget.TYPE,
        AnnotationTarget.CLASS,
        AnnotationTarget.VALUE_PARAMETER
    )
    @StringDef(
        ServiceActionCommand.DELAY,
        ServiceActionCommand.NEW_ID,
        ServiceActionCommand.SET_DISABLE_NETWORK,
        ServiceActionCommand.START_TOR,
        ServiceActionCommand.STOP_SERVICE,
        ServiceActionCommand.STOP_TOR
    )
    @Retention(AnnotationRetention.SOURCE)
    internal annotation class ServiceActionCommand {
        companion object {
            private const val ACTION_COMMAND = "Command_"
            const val DELAY = "${ACTION_COMMAND}DELAY"
            const val NEW_ID = "${ACTION_COMMAND}NEW_ID"
            const val SET_DISABLE_NETWORK = "${ACTION_COMMAND}SET_DISABLE_NETWORK"
            const val START_TOR = "${ACTION_COMMAND}START_TOR"
            const val STOP_SERVICE = "${ACTION_COMMAND}STOP_SERVICE"
            const val STOP_TOR = "${ACTION_COMMAND}STOP_TOR"
        }
    }
}