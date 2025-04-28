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

import androidx.annotation.WorkerThread
import io.matthewnelson.topl_service_base.ApplicationDefaultTorSettings
import io.matthewnelson.topl_service_base.BaseServiceConsts.PrefKeyString
import io.matthewnelson.topl_service_base.BaseServiceConsts.PrefKeyList
import io.matthewnelson.topl_service_base.BaseServiceConsts.PrefKeyInt
import io.matthewnelson.topl_service_base.BaseServiceConsts.PrefKeyBoolean
import io.matthewnelson.topl_service_base.BaseServiceTorSettings
import io.matthewnelson.topl_service_base.TorServicePrefs

internal class ServiceTorSettings private constructor(
    servicePrefs: TorServicePrefs,
    defaultTorSettings: ApplicationDefaultTorSettings
): BaseServiceTorSettings(servicePrefs, defaultTorSettings) {

    companion object {
        @JvmSynthetic
        fun instantiate(
            servicePrefs: TorServicePrefs,
            defaultTorSettings: ApplicationDefaultTorSettings
        ): ServiceTorSettings =
            ServiceTorSettings(servicePrefs, defaultTorSettings)
    }

    @WorkerThread
    override fun dormantClientTimeoutSave(minutes: Int?) {
        if (minutes == defaultTorSettings.dormantClientTimeout) {
            servicePrefs.remove(PrefKeyInt.DORMANT_CLIENT_TIMEOUT)
        } else {
            servicePrefs.putInt(PrefKeyInt.DORMANT_CLIENT_TIMEOUT, minutes)
        }
    }

    @WorkerThread
    override fun disableNetworkSave(boolean: Boolean) {
        if (boolean == defaultTorSettings.disableNetwork) {
            servicePrefs.remove(PrefKeyBoolean.DISABLE_NETWORK)
        } else {
            servicePrefs.putBoolean(PrefKeyBoolean.DISABLE_NETWORK, boolean)
        }
    }

    @WorkerThread
    @Throws(IllegalArgumentException::class)
    override fun dnsPortSave(dnsPort: String) {
        when {
            dnsPort == defaultTorSettings.dnsPort -> {
                servicePrefs.remove(PrefKeyString.DNS_PORT)
            }
            checkPortSelection(dnsPort) -> {
                servicePrefs.putString(PrefKeyString.DNS_PORT, dnsPort)
            }
            else -> {
                throw IllegalArgumentException(
                    "DNS Port must be 0 (disabled), auto, or between 1024 and 65535"
                )
            }
        }
    }

    @WorkerThread
    override fun dnsPortIsolationFlagsSave(isolationFlags: List<@IsolationFlag String>) {
        if (isolationFlags == defaultTorSettings.dnsPortIsolationFlags) {
            servicePrefs.remove(PrefKeyList.DNS_PORT_ISOLATION_FLAGS)
        } else {
            servicePrefs.putList(PrefKeyList.DNS_PORT_ISOLATION_FLAGS, isolationFlags)
        }
    }

    @WorkerThread
    override fun customTorrcSave(customTorrc: String?) {
        if (customTorrc == defaultTorSettings.customTorrc) {
            servicePrefs.remove(PrefKeyString.CUSTOM_TORRC)
        } else {
            servicePrefs.putString(PrefKeyString.CUSTOM_TORRC, customTorrc)
        }
    }

    @WorkerThread
    override fun entryNodesSave(entryNodes: String?) {
        if (entryNodes == defaultTorSettings.entryNodes) {
            servicePrefs.remove(PrefKeyString.ENTRY_NODES)
        } else {
            servicePrefs.putString(PrefKeyString.ENTRY_NODES, entryNodes)
        }
    }

    @WorkerThread
    override fun excludeNodesSave(excludeNodes: String?) {
        if (excludeNodes == defaultTorSettings.excludeNodes) {
            servicePrefs.remove(PrefKeyString.EXCLUDED_NODES)
        } else {
            servicePrefs.putString(PrefKeyString.EXCLUDED_NODES, excludeNodes)
        }
    }

    @WorkerThread
    override fun exitNodesSave(exitNodes: String?) {
        if (exitNodes == defaultTorSettings.exitNodes) {
            servicePrefs.remove(PrefKeyString.EXIT_NODES)
        } else {
            servicePrefs.putString(PrefKeyString.EXIT_NODES, exitNodes)
        }
    }

    @WorkerThread
    @Throws(IllegalArgumentException::class)
    override fun httpTunnelPortSave(httpPort: String) {
        when {
            httpPort == defaultTorSettings.httpTunnelPort -> {
                servicePrefs.remove(PrefKeyString.HTTP_TUNNEL_PORT)
            }
            checkPortSelection(httpPort) -> {
                servicePrefs.putString(PrefKeyString.HTTP_TUNNEL_PORT, httpPort)
            }
            else -> {
                throw IllegalArgumentException(
                    "HTTP Port must be 0 (disabled), auto, or between 1024 and 65535"
                )
            }
        }
    }

    @WorkerThread
    override fun httpPortIsolationFlagsSave(isolationFlags: List<@IsolationFlag String>) {
        if (isolationFlags == defaultTorSettings.httpTunnelPortIsolationFlags) {
            servicePrefs.remove(PrefKeyList.HTTP_TUNNEL_PORT_ISOLATION_FLAGS)
        } else {
            servicePrefs.putList(PrefKeyList.HTTP_TUNNEL_PORT_ISOLATION_FLAGS, isolationFlags)
        }
    }

    @WorkerThread
    override fun proxyHostSave(proxyHost: String?) {
        if (proxyHost == defaultTorSettings.proxyHost) {
            servicePrefs.remove(PrefKeyString.PROXY_HOST)
        } else {
            servicePrefs.putString(PrefKeyString.PROXY_HOST, proxyHost)
        }
    }

    @WorkerThread
    override fun proxyPasswordSave(proxyPassword: String?) {
        if (proxyPassword == defaultTorSettings.proxyPassword) {
            servicePrefs.remove(PrefKeyString.PROXY_PASSWORD)
        } else {
            servicePrefs.putString(PrefKeyString.PROXY_PASSWORD, proxyPassword)
        }
    }

    @WorkerThread
    @Throws(IllegalArgumentException::class)
    override fun proxyPortSave(proxyPort: Int?) {
        when {
            proxyPort == defaultTorSettings.proxyPort -> {
                servicePrefs.remove(PrefKeyInt.PROXY_PORT)
            }
            proxyPort == null || checkPortSelection(proxyPort, checkZero = false) -> {
                servicePrefs.putInt(PrefKeyInt.PROXY_PORT, proxyPort)
            }
            else -> {
                throw IllegalArgumentException(
                    "Proxy Port must null, or between 1024 and 65535"
                )
            }
        }
    }

    @WorkerThread
    override fun proxySocks5HostSave(proxySocks5Host: String?) {
        if (proxySocks5Host == defaultTorSettings.proxySocks5Host) {
            servicePrefs.remove(PrefKeyString.PROXY_SOCKS5_HOST)
        } else {
            servicePrefs.putString(PrefKeyString.PROXY_SOCKS5_HOST, proxySocks5Host)
        }
    }

    @WorkerThread
    @Throws(IllegalArgumentException::class)
    override fun proxySocks5ServerPortSave(proxySocks5ServerPort: Int?) {
        when {
            proxySocks5ServerPort == defaultTorSettings.proxySocks5ServerPort -> {
                servicePrefs.remove(PrefKeyInt.PROXY_SOCKS5_SERVER_PORT)
            }
            proxySocks5ServerPort == null || checkPortSelection(proxySocks5ServerPort, checkZero = false) -> {
                servicePrefs.putInt(PrefKeyInt.PROXY_SOCKS5_SERVER_PORT, proxySocks5ServerPort)
            }
            else -> {
                throw IllegalArgumentException(
                    "ProxySocks5Server Port must null, or between 1024 and 65535"
                )
            }
        }
    }

    @WorkerThread
    @Throws(IllegalArgumentException::class)
    override fun proxyTypeSave(@ProxyType proxyType: String) {
        when (proxyType) {
            ProxyType.DISABLED,
            ProxyType.HTTPS,
            ProxyType.SOCKS_5 -> {
                if (proxyType == defaultTorSettings.proxyType) {
                    servicePrefs.remove(PrefKeyString.PROXY_TYPE)
                } else {
                    servicePrefs.putString(PrefKeyString.PROXY_TYPE, proxyType)
                }
            }
            else -> {
                throw IllegalArgumentException(
                    "ProxyType must be '' (empty/disabled), HTTPS, or Socks5"
                )
            }
        }
    }

    @WorkerThread
    override fun proxyUserSave(proxyUser: String?) {
        if (proxyUser == defaultTorSettings.proxyUser) {
            servicePrefs.remove(PrefKeyString.PROXY_USER)
        } else {
            servicePrefs.putString(PrefKeyString.PROXY_USER, proxyUser)
        }
    }

    @WorkerThread
    override fun reachableAddressPortsSave(reachableAddressPorts: String) {
        if (reachableAddressPorts == defaultTorSettings.reachableAddressPorts) {
            servicePrefs.remove(PrefKeyString.REACHABLE_ADDRESS_PORTS)
        } else {
            servicePrefs.putString(PrefKeyString.REACHABLE_ADDRESS_PORTS, reachableAddressPorts)
        }
    }

    @WorkerThread
    override fun relayNicknameSave(relayNickname: String) {
        if (relayNickname == defaultTorSettings.relayNickname) {
            servicePrefs.remove(PrefKeyString.RELAY_NICKNAME)
        } else {
            servicePrefs.putString(PrefKeyString.RELAY_NICKNAME, relayNickname)
        }
    }

    @WorkerThread
    @Throws(IllegalArgumentException::class)
    override fun relayPortSave(relayPort: String) {
        when {
            relayPort == defaultTorSettings.relayPort -> {
                servicePrefs.remove(PrefKeyString.RELAY_PORT)
            }
            checkPortSelection(relayPort) -> {
                servicePrefs.putString(PrefKeyString.RELAY_PORT, relayPort)
            }
            else -> {
                throw IllegalArgumentException(
                    "Relay Port must be 0 (disabled), auto, or between 1024 and 65535"
                )
            }
        }
    }

    @WorkerThread
    @Throws(IllegalArgumentException::class)
    override fun socksPortSave(socksPort: String) {
        when {
            socksPort == defaultTorSettings.socksPort -> {
                servicePrefs.remove(PrefKeyString.SOCKS_PORT)
            }
            checkPortSelection(socksPort) -> {
                servicePrefs.putString(PrefKeyString.SOCKS_PORT, socksPort)
            }
            else -> {
                throw IllegalArgumentException(
                    "Socks Port must be 0 (disabled), auto, or between 1024 and 65535"
                )
            }
        }
    }

    @WorkerThread
    override fun socksPortIsolationFlagsSave(isolationFlags: List<@IsolationFlag String>) {
        if (isolationFlags == defaultTorSettings.socksPortIsolationFlags) {
            servicePrefs.remove(PrefKeyList.SOCKS_PORT_ISOLATION_FLAGS)
        } else {
            servicePrefs.putList(PrefKeyList.SOCKS_PORT_ISOLATION_FLAGS, isolationFlags)
        }
    }

    @WorkerThread
    override fun virtualAddressNetworkSave(virtualAddressNetwork: String) {
        if (virtualAddressNetwork == defaultTorSettings.virtualAddressNetwork) {
            servicePrefs.remove(PrefKeyString.VIRTUAL_ADDRESS_NETWORK)
        } else {
            servicePrefs.putString(PrefKeyString.VIRTUAL_ADDRESS_NETWORK, virtualAddressNetwork)
        }
    }

    @WorkerThread
    override fun hasBridgesSave(boolean: Boolean) {
        if (boolean == defaultTorSettings.hasBridges) {
            servicePrefs.remove(PrefKeyBoolean.HAS_BRIDGES)
        } else {
            servicePrefs.putBoolean(PrefKeyBoolean.HAS_BRIDGES, boolean)
        }
    }

    @WorkerThread
    @Throws(IllegalArgumentException::class)
    override fun connectionPaddingSave(@ConnectionPadding connectionPadding: String) {
        when (connectionPadding) {
            ConnectionPadding.AUTO,
            ConnectionPadding.OFF,
            ConnectionPadding.ON -> {
                if (connectionPadding == defaultTorSettings.connectionPadding) {
                    servicePrefs.remove(PrefKeyString.HAS_CONNECTION_PADDING)
                } else {
                    servicePrefs.putString(PrefKeyString.HAS_CONNECTION_PADDING, connectionPadding)
                }
            }
            else -> {
                throw IllegalArgumentException(
                    "ConnectionPadding must be 0 (Off), 1 (On), or auto"
                )
            }
        }
    }

    @WorkerThread
    override fun hasCookieAuthenticationSave(boolean: Boolean) {
        if (boolean == defaultTorSettings.hasCookieAuthentication) {
            servicePrefs.remove(PrefKeyBoolean.HAS_COOKIE_AUTHENTICATION)
        } else {
            servicePrefs.putBoolean(PrefKeyBoolean.HAS_COOKIE_AUTHENTICATION, boolean)
        }
    }

    @WorkerThread
    override fun hasDebugLogsSave(boolean: Boolean) {
        if (boolean == defaultTorSettings.hasDebugLogs) {
            servicePrefs.remove(PrefKeyBoolean.HAS_DEBUG_LOGS)
        } else {
            servicePrefs.putBoolean(PrefKeyBoolean.HAS_DEBUG_LOGS, boolean)
        }
    }

    @WorkerThread
    override fun hasDormantCanceledByStartupSave(boolean: Boolean) {
        if (boolean == defaultTorSettings.hasDormantCanceledByStartup) {
            servicePrefs.remove(PrefKeyBoolean.HAS_DORMANT_CANCELED_BY_STARTUP)
        } else {
            servicePrefs.putBoolean(PrefKeyBoolean.HAS_DORMANT_CANCELED_BY_STARTUP, boolean)
        }
    }

    @WorkerThread
    override fun hasOpenProxyOnAllInterfacesSave(boolean: Boolean) {
        if (boolean == defaultTorSettings.hasOpenProxyOnAllInterfaces) {
            servicePrefs.remove(PrefKeyBoolean.HAS_OPEN_PROXY_ON_ALL_INTERFACES)
        } else {
            servicePrefs.putBoolean(PrefKeyBoolean.HAS_OPEN_PROXY_ON_ALL_INTERFACES, boolean)
        }
    }

    @WorkerThread
    override fun hasReachableAddressSave(boolean: Boolean) {
        if (boolean == defaultTorSettings.hasReachableAddress) {
            servicePrefs.remove(PrefKeyBoolean.HAS_REACHABLE_ADDRESS)
        } else {
            servicePrefs.putBoolean(PrefKeyBoolean.HAS_REACHABLE_ADDRESS, boolean)
        }
    }

    @WorkerThread
    override fun hasReducedConnectionPaddingSave(boolean: Boolean) {
        if (boolean == defaultTorSettings.hasReducedConnectionPadding) {
            servicePrefs.remove(PrefKeyBoolean.HAS_REDUCED_CONNECTION_PADDING)
        } else {
            servicePrefs.putBoolean(PrefKeyBoolean.HAS_REDUCED_CONNECTION_PADDING, boolean)
        }
    }

    @WorkerThread
    override fun hasSafeSocksSave(boolean: Boolean) {
        if (boolean == defaultTorSettings.hasSafeSocks) {
            servicePrefs.remove(PrefKeyBoolean.HAS_SAFE_SOCKS)
        } else {
            servicePrefs.putBoolean(PrefKeyBoolean.HAS_SAFE_SOCKS, boolean)
        }
    }

    @WorkerThread
    override fun hasStrictNodesSave(boolean: Boolean) {
        if (boolean == defaultTorSettings.hasStrictNodes) {
            servicePrefs.remove(PrefKeyBoolean.HAS_STRICT_NODES)
        } else {
            servicePrefs.putBoolean(PrefKeyBoolean.HAS_STRICT_NODES, boolean)
        }
    }

    @WorkerThread
    override fun hasTestSocksSave(boolean: Boolean) {
        if (boolean == defaultTorSettings.hasTestSocks) {
            servicePrefs.remove(PrefKeyBoolean.HAS_TEST_SOCKS)
        } else {
            servicePrefs.putBoolean(PrefKeyBoolean.HAS_TEST_SOCKS, boolean)
        }
    }

    @WorkerThread
    override fun isAutoMapHostsOnResolveSave(boolean: Boolean) {
        if (boolean == defaultTorSettings.isAutoMapHostsOnResolve) {
            servicePrefs.remove(PrefKeyBoolean.IS_AUTO_MAP_HOSTS_ON_RESOLVE)
        } else {
            servicePrefs.putBoolean(PrefKeyBoolean.IS_AUTO_MAP_HOSTS_ON_RESOLVE, boolean)
        }
    }

    @WorkerThread
    override fun isRelaySave(boolean: Boolean) {
        if (boolean == defaultTorSettings.isRelay) {
            servicePrefs.remove(PrefKeyBoolean.IS_RELAY)
        } else {
            servicePrefs.putBoolean(PrefKeyBoolean.IS_RELAY, boolean)
        }
    }

    @WorkerThread
    override fun runAsDaemonSave(boolean: Boolean) {
        if (boolean == defaultTorSettings.runAsDaemon) {
            servicePrefs.remove(PrefKeyBoolean.RUN_AS_DAEMON)
        } else {
            servicePrefs.putBoolean(PrefKeyBoolean.RUN_AS_DAEMON, boolean)
        }
    }

    @WorkerThread
    @Throws(IllegalArgumentException::class)
    override fun transPortSave(transPort: String) {
        when {
            transPort == defaultTorSettings.transPort -> {
                servicePrefs.remove(PrefKeyString.TRANS_PORT)
            }
            checkPortSelection(transPort) -> {
                servicePrefs.putString(PrefKeyString.TRANS_PORT, transPort)
            }
            else -> {
                throw IllegalArgumentException(
                    "Socks Trans Port must be 0 (disabled), auto, or between 1024 and 65535"
                )
            }
        }
    }

    @WorkerThread
    override fun transPortIsolationFlagsSave(isolationFlags: List<@IsolationFlag String>) {
        if (isolationFlags == defaultTorSettings.transPortIsolationFlags) {
            servicePrefs.remove(PrefKeyList.TRANS_PORT_ISOLATION_FLAGS)
        } else {
            servicePrefs.putList(PrefKeyList.TRANS_PORT_ISOLATION_FLAGS, isolationFlags)
        }
    }

    @WorkerThread
    override fun useSocks5Save(boolean: Boolean) {
        if (boolean == defaultTorSettings.useSocks5) {
            servicePrefs.remove(PrefKeyBoolean.USE_SOCKS5)
        } else {
            servicePrefs.putBoolean(PrefKeyBoolean.USE_SOCKS5, boolean)
        }
    }

    private fun checkPortSelection(port: Int, checkZero: Boolean): Boolean =
        checkPortSelection(port.toString(), checkAuto = false, checkZero = checkZero)

    private fun checkPortSelection(
        port: String,
        checkAuto: Boolean = true,
        checkZero: Boolean = true
    ): Boolean {
        if (checkAuto && port == PortOption.AUTO) return true

        return try {
            val portInt = port.toInt()
            if (checkZero && portInt == 0) {
                return true
            }
            portInt in 1024..65535
        } catch (e: Exception) {
            false
        }
    }
}