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

import android.app.Application
import androidx.core.app.NotificationCompat
import io.matthewnelson.topl_service.TorServiceController
import io.matthewnelson.topl_service.lifecycle.BackgroundManager
import io.matthewnelson.topl_service.notification.ServiceNotification
import org.matrix.android.sdk.api.settings.LightweightSettingsStorage
import javax.inject.Inject

class TorSetup @Inject constructor(
        private val torEventBroadcaster: TorEventBroadcaster,
        private val lightweightSettingsStorage: LightweightSettingsStorage
) {
    fun generateTorServiceControllerBuilder(application: Application): TorServiceController.Builder {
        return TorServiceController.Builder(
                application,
                generateTorServiceNotificationBuilder(),
                generateBackgroundManagerPolicy(),
                1,
                TorSettings(
                        context = application,
                        lightweightSettingsStorage.getTorBridge().takeIf { !it.isNullOrBlank() }
                ),
                "geoip",
                "geoip6"
        )
                .addTimeToDisableNetworkDelay(1000L)
                .addTimeToRestartTorDelay(100L)
                .addTimeToStopServiceDelay(100L)
                .disableStopServiceOnTaskRemoved(true)
                .setEventBroadcaster(torEventBroadcaster)
                .setBuildConfigDebug(true)
    }

    private fun generateTorServiceNotificationBuilder(): ServiceNotification.Builder {
        return ServiceNotification.Builder(
                "Tor",
                "Tor",
                "tor service",
                21
        )
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .showNotification(true)
    }

    private fun generateBackgroundManagerPolicy(): BackgroundManager.Builder.Policy {
        return BackgroundManager.Builder()
                .runServiceInForeground(killAppIfTaskIsRemoved = true)
    }
}
