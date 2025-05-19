/*
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package im.vector.app.features.settings

import androidx.fragment.app.Fragment

abstract class ComposePreferenceFragment : Fragment() {
    // TODO: this only handle title, i can remove this later
    abstract var titleRes: Int

    override fun onResume() {
        (activity as? VectorSettingsActivity)?.supportActionBar?.setTitle(titleRes)
        return super.onResume()
    }
}
