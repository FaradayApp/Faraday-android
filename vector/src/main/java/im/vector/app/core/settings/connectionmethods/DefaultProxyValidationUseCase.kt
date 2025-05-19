/*
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package im.vector.app.core.settings.connectionmethods

import im.vector.app.core.resources.StringProvider
import im.vector.lib.strings.CommonStrings
import org.matrix.android.sdk.api.util.ProxyType
import org.matrix.android.sdk.internal.proxy.ValidateProxyUseCase
import javax.inject.Inject

class DefaultValidateProxyUseCase @Inject constructor(
        private val stringProvider: StringProvider
) : ValidateProxyUseCase {
    override fun validate(type: ProxyType, host: String, port: String, username: String, password: String): Result<Unit> {
        val errors = mutableListOf<ProxyValidationError>()

        if (type == ProxyType.NO_PROXY) return Result.success(Unit)

        if (host.isBlank() || !HOST_REGEX.toRegex().matches(host)) {
            errors += ProxyValidationError.InvalidHostError(
                    message = stringProvider.getString(CommonStrings.error_in_host_address)
            )
        }

        if (port.isBlank() || !PORT_REGEX.toRegex().matches(port)) {
            errors += ProxyValidationError.InvalidPortError(
                    message = stringProvider.getString(CommonStrings.error_in_port_number)
            )
        }

        if (username.isNotBlank() || password.isNotBlank()) {
            if (username.isBlank()) {
                errors += ProxyValidationError.InvalidUsernameError(
                        message = stringProvider.getString(CommonStrings.error_in_proxy_provide_username)
                )
            }
            if (password.isBlank()) {
                errors += ProxyValidationError.InvalidPasswordError(
                        message = stringProvider.getString(CommonStrings.error_in_proxy_provide_password)
                )
            }
        }

        return if (errors.isEmpty()) Result.success(Unit)
        else Result.failure(ProxyValidationException(errors))
    }

    companion object {
        // TODO: add support for domains and port string aliases
        private const val HOST_REGEX = "^(?:(\\w+)(?::(\\w+))?@)?((?:\\d{1,3})(?:\\.\\d{1,3}){3})(?::(\\d{1,5}))?\$"
        private const val PORT_REGEX = "^([1-9][0-9]{0,3}|[1-5][0-9]{4}|6[0-4][0-9]{3}|65[0-4][0-9]{2}|655[0-2][0-9]|6553[0-5])\$"
    }
}

class ProxyValidationException(val errors: List<ProxyValidationError>)
    : Exception("Invalid proxy: ${errors.joinToString("\n\t")}")

sealed class ProxyValidationError(val message: String) {
    class InvalidHostError(message: String) : ProxyValidationError(message)
    class InvalidPortError(message: String) : ProxyValidationError(message)
    class InvalidUsernameError(message: String) : ProxyValidationError(message)
    class InvalidPasswordError(message: String) : ProxyValidationError(message)

    override fun toString(): String = "${this::class.simpleName}: $message"
}
