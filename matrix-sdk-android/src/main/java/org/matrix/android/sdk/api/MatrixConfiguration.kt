/*
 * Copyright 2020 The Matrix.org Foundation C.I.C.
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

package org.matrix.android.sdk.api

import okhttp3.ConnectionSpec
import okhttp3.Interceptor
import org.matrix.android.sdk.api.crypto.MXCryptoConfig
import org.matrix.android.sdk.api.metrics.CryptoMetricPlugin
import org.matrix.android.sdk.api.metrics.MetricPlugin
import org.matrix.android.sdk.api.provider.CustomEventTypesProvider
import org.matrix.android.sdk.api.provider.MatrixItemDisplayNameFallbackProvider
import org.matrix.android.sdk.api.provider.RoomDisplayNameFallbackProvider
import java.net.Proxy

data class MatrixConfiguration(
        val applicationFlavor: String = "Default-application-flavor",
        val cryptoConfig: MXCryptoConfig = MXCryptoConfig(),
        val integrationUIUrl: String = "https://scalar.vector.im/",
        val integrationRestUrl: String = "https://scalar.vector.im/api",
        val integrationWidgetUrls: List<String> = listOf(
                "https://scalar.vector.im/_matrix/integrations/v1",
                "https://scalar.vector.im/api",
                "https://scalar-staging.vector.im/_matrix/integrations/v1",
                "https://scalar-staging.vector.im/api",
                "https://scalar-staging.riot.im/scalar/api"
        ),
        /**
         * Optional base url to create client permalinks (eg. https://www.example.com/#/) instead of Matrix ones (matrix.to links).
         * Do not forget to add the "#" which is required by the permalink parser.
         *
         * Note: this field is only used for permalinks creation, you will also have to edit the string-array `permalink_supported_hosts` in the config file
         * and add it to your manifest to handle these links in the application.
         */
        val clientPermalinkBaseUrl: String? = null,
        /**
         * Optional proxy to connect to the matrix servers.
         * You can create one using for instance Proxy(proxyType, InetSocketAddress.createUnresolved(hostname, port).
         */
        val proxy: Proxy? = null,
        /**
         * TLS versions and cipher suites limitation for unauthenticated requests.
         */
        val connectionSpec: ConnectionSpec = ConnectionSpec.RESTRICTED_TLS,
        /**
         * True to advertise support for call transfers to other parties on Matrix calls.
         */
        val supportsCallTransfer: Boolean = false,
        /**
         * MatrixItemDisplayNameFallbackProvider to provide default display name for MatrixItem. By default, the id will be used
         */
        val matrixItemDisplayNameFallbackProvider: MatrixItemDisplayNameFallbackProvider? = null,
        /**
         * RoomDisplayNameFallbackProvider to provide default room display name.
         */
        val roomDisplayNameFallbackProvider: RoomDisplayNameFallbackProvider,
        /**
         * Thread messages default enable/disabled value.
         */
        val threadMessagesEnabledDefault: Boolean = true,
        /**
         * Application password default enable/disabled value.
         */
        val applicationPasswordEnabledDefault: Boolean = false,
        /**
         * Custom settings default enable/disabled value.
         */
        val customSettingsEnabledDefault: Boolean = false,
        /**
         * Connection default proxy host value.
         */
        val connectionProxyHostDefault: String = "",
        /**
         * Connection default proxy port value.
         */
        val connectionProxyPortDefault: Int = 0,
        /**
         * Connection default proxy auth required value.
         */
        val connectionProxyAuthRequired: Boolean = false,
        /**
         * Connection default proxy username value.
         */
        val connectionProxyUsernameDefault: String = "",
        /**
         * Connection default proxy password value..
         */
        val connectionProxyPasswordDefault: String = "",
        /**
         * List of network interceptors, they will be added when building an OkHttp client.
         */
        val networkInterceptors: List<Interceptor> = emptyList(),
        /**
         * Sync configuration.
         */
        val syncConfig: SyncConfig = SyncConfig(),
        /**
         * Metrics plugin that can be used to capture metrics from matrix-sdk-android.
         */
        val metricPlugins: List<MetricPlugin> = emptyList(),

        val cryptoAnalyticsPlugin: CryptoMetricPlugin? = null,
        /**
         * CustomEventTypesProvider to provide custom event types to the sdk which should be processed with internal events.
         */
        val customEventTypesProvider: CustomEventTypesProvider? = null,
)
