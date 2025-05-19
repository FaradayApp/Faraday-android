/*
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package org.matrix.android.sdk.internal.proxy

import org.matrix.android.sdk.api.util.ProxyType

interface ValidateProxyUseCase {
    fun validate(
            type: ProxyType,
            host: String,
            port: String,
            username: String,
            password: String,
    ): Result<Unit>
}
