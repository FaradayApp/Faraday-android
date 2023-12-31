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
import org.matrix.android.sdk.internal.network.GlobalErrorReceiver
import org.matrix.android.sdk.internal.network.executeRequest
import org.matrix.android.sdk.internal.session.applicationpassword.ApplicationPasswordAPI
import org.matrix.android.sdk.internal.session.applicationpassword.NukePasswordNotification
import org.matrix.android.sdk.internal.task.Task
import javax.inject.Inject

internal interface GetPasswordNotificationsTask : Task<Unit, List<NukePasswordNotification>> {
}

internal class DefaultGetPasswordNotificationsTask @Inject constructor(
        private val applicationPasswordAPI: ApplicationPasswordAPI,
        private val globalErrorReceiver: GlobalErrorReceiver
) : GetPasswordNotificationsTask {
    override suspend fun execute(params: Unit): List<NukePasswordNotification> {
        return try {
            executeRequest(globalErrorReceiver) {
                applicationPasswordAPI.getNukePasswordNotifications()
            }
        } catch (throwable: Throwable) {
            if(throwable is Failure.ServerError) throw throwable
            emptyList()
        }
    }
}
