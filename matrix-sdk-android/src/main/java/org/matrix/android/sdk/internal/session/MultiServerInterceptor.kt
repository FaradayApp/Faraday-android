/*
 * Copyright (c) 2024 New Vector Ltd
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

package org.matrix.android.sdk.internal.session

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import timber.log.Timber
import javax.inject.Inject

class MultiServerInterceptor @Inject constructor(): Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val homeServerUrl = HomeServerHolder.homeServer
        var request = chain.request()
        val newRequestBuilder = chain.request().newBuilder()

        homeServerUrl.takeIf { it.isNotBlank() }?.let {
            newRequestBuilder.url(
                    request.url.toString().replace(
                            LOCALHOST_DOMAIN, it.let {
                                if(!it.endsWith("/"))
                                    it.plus("/")
                                else it
                            }
                    ).toHttpUrlOrNull() ?: request.url
            )
        }

        request = newRequestBuilder.build()
        Timber.d("Sending to ${request.url}")
        return chain.proceed(request)
    }
}