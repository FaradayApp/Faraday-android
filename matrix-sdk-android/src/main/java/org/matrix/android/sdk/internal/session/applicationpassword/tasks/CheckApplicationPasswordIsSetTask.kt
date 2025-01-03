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

package org.matrix.android.sdk.internal.session.applicationpassword.tasks

import org.matrix.android.sdk.api.failure.Failure
import org.matrix.android.sdk.api.settings.LightweightSettingsStorage
import org.matrix.android.sdk.internal.network.GlobalErrorReceiver
import org.matrix.android.sdk.internal.network.executeRequest
import org.matrix.android.sdk.internal.session.applicationpassword.ApplicationPasswordAPI
import org.matrix.android.sdk.internal.task.Task
import javax.inject.Inject


internal interface CheckApplicationPasswordIsSetTask : Task<Unit, Boolean> {
}

internal class DefaultCheckApplicationPasswordIsSetTask @Inject constructor(
        private val applicationPasswordAPI: ApplicationPasswordAPI,
        private val globalErrorReceiver: GlobalErrorReceiver,
        private val lightweightSettingsStorage: LightweightSettingsStorage
) : CheckApplicationPasswordIsSetTask {
    override suspend fun execute(params: Unit): Boolean {
        if(true /* !lightweightSettingsStorage.areCustomSettingsEnabled() */) return false
        val result = try {
            executeRequest(globalErrorReceiver) {
                applicationPasswordAPI.checkApplicationPasswordIsSet()
            }.status
        } catch (throwable: Throwable) {
            if(throwable is Failure.ServerError) throw throwable
            false
        }
        return result == "Application password set"
    }
}
