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

import io.matthewnelson.topl_core.util.TorInstaller
import io.matthewnelson.topl_core_base.TorConfigFiles
import io.matthewnelson.topl_service.R
import io.matthewnelson.topl_service.TorServiceController
import io.matthewnelson.topl_service_base.TorServicePrefs
import io.matthewnelson.topl_service.service.BaseService
import io.matthewnelson.topl_service_base.BaseServiceConsts.PrefKeyList
import java.io.*
import java.util.concurrent.TimeoutException

/**
 * Installs things needed for Tor.
 *
 * See [io.matthewnelson.topl_service.TorServiceController.Builder]
 *
 * @param [torService] [BaseService] for context
 * */
internal class ServiceTorInstaller private constructor(
    private val torService: BaseService
): TorInstaller() {

    companion object {
        @JvmSynthetic
        fun instantiate(torService: BaseService): ServiceTorInstaller =
            ServiceTorInstaller(torService)

        private const val APP_VERSION_CODE = "APP_VERSION_CODE"
    }

    private val torConfigFiles: TorConfigFiles
        get() = TorServiceController.getTorConfigFiles()

    private var geoIpFileCopied = false
    private var geoIpv6FileCopied = false

    // broadcastLogger is available from TorInstaller and is instantiated as soon as
    // OnionProxyManager gets initialized.
//    var broadcastLogger: BroadcastLogger? = null

    @Throws(IOException::class, SecurityException::class)
    override fun setup() {
        if (!torConfigFiles.geoIpFile.exists()) {
            copyGeoIpAsset()
            geoIpFileCopied = true
        }
        if (!torConfigFiles.geoIpv6File.exists()) {
            copyGeoIpv6Asset()
            geoIpv6FileCopied = true
        }
        if (!torConfigFiles.v3AuthPrivateDir.exists()) {
            torConfigFiles.v3AuthPrivateDir.mkdirs()
        }

        val localPrefs = BaseService.getLocalPrefs(torService.getContext())

        // If the app version has been increased, or if this is a debug build, copy over
        // geoip assets then update SharedPreferences with the new version code. This
        // mitigates copying to be done only if a version upgrade is had.
        if (BaseService.getBuildConfigDebug() ||
            BaseService.getBuildConfigVersionCode() > localPrefs.getInt(APP_VERSION_CODE, -1)
        ) {
            if (!geoIpFileCopied) {
                copyGeoIpAsset()
            }
            if (!geoIpv6FileCopied) {
                copyGeoIpv6Asset()
            }
            localPrefs.edit()
                .putInt(APP_VERSION_CODE, BaseService.getBuildConfigVersionCode())
                .apply()
        }
    }

    private fun copyGeoIpAsset() {
        synchronized(torConfigFiles.geoIpFileLock) {
            torService.copyAsset(BaseService.getGeoipAssetPath(), torConfigFiles.geoIpFile)
            broadcastLogger?.debug(
                "Asset copied from ${BaseService.getGeoipAssetPath()} -> ${torConfigFiles.geoIpFile}"
            )
        }
    }

    private fun copyGeoIpv6Asset() {
        synchronized(torConfigFiles.geoIpv6FileLock) {
            torService.copyAsset(BaseService.getGeoip6AssetPath(), torConfigFiles.geoIpv6File)
            broadcastLogger?.debug(
                "Asset copied from ${BaseService.getGeoip6AssetPath()} -> ${torConfigFiles.geoIpv6File}"
            )
        }
    }

    @Throws(IOException::class, TimeoutException::class)
    override fun updateTorConfigCustom(content: String?) {

    }

    @Throws(IOException::class)
    override fun openBridgesStream(): InputStream? {
        /*
            BridgesList is an overloaded field, which can cause some confusion.

            The list can be:
              1) a filter like obfs4, meek, or snowflake OR
              2) it can be a custom bridge

            For (1), we just pass back all bridges, the filter will occur
              elsewhere in the library.
            For (2) we return the bridge list as a raw stream.

            If length is greater than 9, then we know this is a custom bridge
        * */
        // TODO: Completely refactor how bridges work.
        val userDefinedBridgeList: String = TorServicePrefs(torService.getContext())
            .getList(PrefKeyList.USER_DEFINED_BRIDGES, arrayListOf())
            .joinToString()

        var bridgeType = (if (userDefinedBridgeList.length > 9) 1 else 0).toByte()
        // Terrible hack. Must keep in sync with topl::addBridgesFromResources.
        if (bridgeType.toInt() == 0) {
            when (userDefinedBridgeList) {
                SupportedBridgeType.OBFS4 -> bridgeType = 2
                SupportedBridgeType.MEEK -> bridgeType = 3
                SupportedBridgeType.SNOWFLAKE -> bridgeType = 4
            }
        }

        val bridgeTypeStream = ByteArrayInputStream(byteArrayOf(bridgeType))
        val bridgeStream =
            if (bridgeType.toInt() == 1) {
                ByteArrayInputStream(userDefinedBridgeList.toByteArray())
            } else {
                torService.getContext().resources.openRawResource(R.raw.bridges)
            }
        return SequenceInputStream(bridgeTypeStream, bridgeStream)
    }
}