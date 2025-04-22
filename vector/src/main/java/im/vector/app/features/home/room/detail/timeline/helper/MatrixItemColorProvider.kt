/*
 * Copyright 2020-2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package im.vector.app.features.home.room.detail.timeline.helper

import android.graphics.Color
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.staticCompositionLocalOf
import im.vector.app.core.resources.ColorProvider
import im.vector.app.features.settings.VectorPreferences
import im.vector.lib.ui.styles.R
import org.matrix.android.sdk.api.util.MatrixItem
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

val LocalMatrixItemColorProvider = staticCompositionLocalOf<MatrixItemColorProvider> { error("MatrixItemColorProvider not provided") }

@Singleton
class MatrixItemColorProvider @Inject constructor(
        private val vectorPreferences: VectorPreferences,
        private val colorProvider: ColorProvider
) {
    // Note: compared to Element, we do not cache the actual color, but the color-attr, to remain theme-safe.
    // For user overrides, however, we still need to store the actual color, so store that separately.
    private val attrCache = mutableMapOf<String, Int>()
    private val overrideCache = mutableMapOf<String, Int>()

    @ColorInt
    @Suppress("UNUSED_PARAMETER")
    fun getColor(matrixItem: MatrixItem, userInRoomInformation: UserInRoomInformation? = null): Int {
        val coloringMode = vectorPreferences.userColorMode(userInRoomInformation?.isDm ?: false, userInRoomInformation?.isPublicRoom ?: false)
        return when (coloringMode) {
            USER_COLORING_FROM_PL -> {
                colorProvider.getColorFromAttribute(
                        when {
                            userInRoomInformation?.userPowerLevel == null -> com.google.android.material.R.attr.colorAccent // This is also used for avatars in the room overview
                            userInRoomInformation.userPowerLevel >= 100 -> R.attr.user_color_pl_100
                            userInRoomInformation.userPowerLevel >= 95 -> R.attr.user_color_pl_95
                            userInRoomInformation.userPowerLevel >= 51 -> R.attr.user_color_pl_51
                            userInRoomInformation.userPowerLevel >= 50 -> R.attr.user_color_pl_50
                            userInRoomInformation.userPowerLevel >= 1 -> R.attr.user_color_pl_1
                            else -> R.attr.user_color_pl_0
                        }
                )
            }
            USER_COLORING_FROM_ID -> {
                return overrideCache[matrixItem.id] ?: colorProvider.getColorFromAttribute(
                        attrCache.getOrPut(matrixItem.id) {
                            when (matrixItem) {
                                is MatrixItem.UserItem -> getColorAttrFromUserId(matrixItem.id)
                                else -> getColorAttrFromRoomId(matrixItem.id)
                            }
                        }
                )
            }
            else -> {
                colorProvider.getColorFromAttribute(android.R.attr.colorAccent)
            }
        }
    }

    @AttrRes
    fun getColorAttrFromUserId(userId: String?): Int {
        var hash = 0

        userId?.toList()?.map { chr -> hash = (hash shl 5) - hash + chr.code }

        return when (abs(hash) % 8) {
            1    -> R.attr.user_color_hash_02
            2    -> R.attr.user_color_hash_03
            3    -> R.attr.user_color_hash_04
            4    -> R.attr.user_color_hash_05
            5    -> R.attr.user_color_hash_06
            6    -> R.attr.user_color_hash_07
            7    -> R.attr.user_color_hash_08
            else -> R.attr.user_color_hash_01
        }
    }

    @AttrRes
    private fun getColorAttrFromRoomId(roomId: String?): Int {
        return when ((roomId?.toList()?.sumOf { it.code } ?: 0) % 3) {
            1    -> R.attr.room_color_hash_02
            2    -> R.attr.room_color_hash_03
            else -> R.attr.room_color_hash_01
        }
    }

    fun setOverrideColors(overrideColors: Map<String, String>?) {
        overrideCache.clear()
        overrideColors?.forEach {
            setOverrideColor(it.key, it.value)
        }
    }

    fun setOverrideColor(id: String, colorSpec: String?): Boolean {
        val color = parseUserColorSpec(colorSpec)
        return if (color == null) {
            overrideCache.remove(id)
            false
        } else {
            overrideCache[id] = color
            true
        }
    }

    @ColorInt
    private fun parseUserColorSpec(colorText: String?): Int? {
        return if (colorText.isNullOrBlank()) {
            null
        } else {
            try {
                if (colorText.length == 1) {
                    colorProvider.getColor(getUserColorByIndex(colorText.toInt()))
                } else {
                    Color.parseColor(colorText)
                }
            } catch (e: Throwable) {
                Timber.e(e, "Unable to parse color $colorText")
                null
            }
        }
    }

    companion object {
        @ColorRes
        @VisibleForTesting
        @Deprecated("Use getColorAttrFromUserId")
        fun getColorFromUserId(userId: String?): Int {
            var hash = 0

            userId?.toList()?.map { chr -> hash = (hash shl 5) - hash + chr.code }

            return getUserColorByIndex(abs(hash))
        }

        @ColorRes
        private fun getUserColorByIndex(index: Int): Int {
            return when (index % 8) {
                1 -> R.color.element_name_02
                2 -> R.color.element_name_03
                3 -> R.color.element_name_04
                4 -> R.color.element_name_05
                5 -> R.color.element_name_06
                6 -> R.color.element_name_07
                7 -> R.color.element_name_08
                else -> R.color.element_name_01
            }
        }

        @ColorRes
        @Deprecated("Use getColorAttrFromRoomId")
        private fun getColorFromRoomId(roomId: String?): Int {
            return when ((roomId?.toList()?.sumOf { it.code } ?: 0) % 3) {
                1 -> R.color.element_room_02
                2 -> R.color.element_room_03
                else -> R.color.element_room_01
            }
        }

        // Same values as in R.array.user_color_mode_values
        public const val USER_COLORING_UNIFORM = "uniform"
        public const val USER_COLORING_FROM_ID = "from-id"
        public const val USER_COLORING_FROM_PL = "from-pl"
        const val USER_COLORING_DEFAULT = USER_COLORING_UNIFORM
    }

    data class UserInRoomInformation(val isDm: Boolean? = null, val isPublicRoom: Boolean? = null, val userPowerLevel: Int? = null)
}
