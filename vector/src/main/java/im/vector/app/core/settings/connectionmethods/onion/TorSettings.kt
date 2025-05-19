/*
 * Copyright (c) 2023 New Vector Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package im.vector.app.core.settings.connectionmethods.onion

import android.content.Context
import io.matthewnelson.topl_service_base.ApplicationDefaultTorSettings

/**
 * Tor Settings. It is possible to set up other obfs4 bridges to circumvent Firewall in case
 * bridges below get blocked in a certain country. Other than that current settings work just fine.
 */

class TorSettings(
        private val context: Context,
        bridge: String?
) : ApplicationDefaultTorSettings() {
    override val connectionPadding = ConnectionPadding.OFF

    override val customTorrc: String = (bridge?.let { if (it.endsWith("\n")) it else it + "\n" } ?: DEFAULT_BRIDGE) +
            "UseBridges 1\n" +
            "ClientTransportPlugin obfs4 exec ${context.applicationInfo.nativeLibraryDir}/libobfs4proxy.so"

    override val disableNetwork = DEFAULT__DISABLE_NETWORK
    override val dnsPort: String = PortOption.DISABLED
    override val dnsPortIsolationFlags: List<String> = listOf(IsolationFlag.ISOLATE_CLIENT_PROTOCOL)
    override val dormantClientTimeout: Int = DEFAULT__DORMANT_CLIENT_TIMEOUT
    override val entryNodes: String = DEFAULT__ENTRY_NODES
    override val excludeNodes: String = DEFAULT__EXCLUDED_NODES
    override val exitNodes: String = DEFAULT__EXIT_NODES
    override val hasBridges: Boolean = DEFAULT__HAS_BRIDGES
    override val hasCookieAuthentication: Boolean = DEFAULT__HAS_COOKIE_AUTHENTICATION
    override val hasDebugLogs: Boolean = DEFAULT__HAS_DEBUG_LOGS
    override val hasDormantCanceledByStartup: Boolean = DEFAULT__HAS_DORMANT_CANCELED_BY_STARTUP
    override val hasOpenProxyOnAllInterfaces: Boolean = DEFAULT__HAS_OPEN_PROXY_ON_ALL_INTERFACES
    override val hasReachableAddress: Boolean = DEFAULT__HAS_REACHABLE_ADDRESS
    override val hasReducedConnectionPadding: Boolean = DEFAULT__HAS_REDUCED_CONNECTION_PADDING
    override val hasSafeSocks: Boolean = DEFAULT__HAS_SAFE_SOCKS
    override val hasStrictNodes: Boolean = DEFAULT__HAS_STRICT_NODES
    override val hasTestSocks: Boolean = DEFAULT__HAS_TEST_SOCKS
    override val httpTunnelPort: String = PortOption.AUTO
    override val httpTunnelPortIsolationFlags: List<String> = listOf(
            IsolationFlag.ISOLATE_CLIENT_PROTOCOL
    )
    override val isAutoMapHostsOnResolve: Boolean = DEFAULT__IS_AUTO_MAP_HOSTS_ON_RESOLVE
    override val isRelay: Boolean = DEFAULT__IS_RELAY
    override val listOfSupportedBridges: List<String> = listOf(
            SupportedBridgeType.MEEK,
            SupportedBridgeType.OBFS4
    )
    override val proxyHost: String = DEFAULT__PROXY_HOST
    override val proxyPassword: String = DEFAULT__PROXY_PASSWORD
    override val proxyPort: Int? = null
    override val proxySocks5Host: String = DEFAULT__PROXY_SOCKS5_HOST
    override val proxySocks5ServerPort: Int? = null
    override val proxyType: String = ProxyType.DISABLED
    override val proxyUser: String = DEFAULT__PROXY_USER
    override val reachableAddressPorts: String = DEFAULT__REACHABLE_ADDRESS_PORTS
    override val relayNickname: String = DEFAULT__RELAY_NICKNAME
    override val relayPort: String = PortOption.DISABLED
    override val runAsDaemon: Boolean = DEFAULT__RUN_AS_DAEMON
    override val socksPort: String = "9050"
    override val socksPortIsolationFlags: List<String> = listOf(
            IsolationFlag.KEEP_ALIVE_ISOLATE_SOCKS_AUTH,
            IsolationFlag.IPV6_TRAFFIC,
            IsolationFlag.PREFER_IPV6,
            IsolationFlag.ISOLATE_CLIENT_PROTOCOL
    )
    override val transPort: String = PortOption.DISABLED
    override val transPortIsolationFlags: List<String> = listOf(
            IsolationFlag.ISOLATE_CLIENT_PROTOCOL
    )
    override val useSocks5: Boolean = DEFAULT__USE_SOCKS5
    override val virtualAddressNetwork: String = "10.192.0.2/10"

    companion object {
        const val DEFAULT_BRIDGE =
                "Bridge obfs4 91.99.54.153:32286 9824B0998959A8F397BEEF10247E2EB4712CF88C cert=gpDhLlt15aorqVCrVVpPVcXGCOsfumw0QXE9H4nKxT+8yKpP2y6CvU0bTvhi/w9U4R1kdw iat-mode=0\n" +
                        "Bridge obfs4 51.89.230.58:31205 BF58F4E5CDB2F2F43A61D82C8CB3288DABF92549 cert=X1CKPqQodiGYduaUTBcdG86en9bCrRpGNDUr3LOgH+XAAZ+01/4oN1rzaLlAZaQLzvQlGQ iat-mode=0\n"
    }
}
