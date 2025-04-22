/*
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package im.vector.app.features.home.avatar

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideSubcomposition
import com.bumptech.glide.integration.compose.RequestState
import im.vector.app.core.dependencies.LocalContentUrlResolver
import im.vector.app.core.resources.LocalStringProvider
import im.vector.app.core.utils.LocalDimensionConverter
import im.vector.app.features.home.room.detail.timeline.helper.LocalMatrixItemColorProvider
import im.vector.app.features.home.room.detail.timeline.helper.MatrixItemColorProvider
import org.matrix.android.sdk.api.util.MatrixItem

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun Avatar(matrixItem: MatrixItem, modifier: Modifier = Modifier) {
    val stringProvider = LocalStringProvider.current
    val contentUrlResolver = LocalContentUrlResolver.current
    val dimensionConverter = LocalDimensionConverter.current

    val resolvedUrl = remember(matrixItem.avatarUrl) {
        contentUrlResolver?.let {
            AvatarHelper.resolvedUrl(it, matrixItem.avatarUrl)
        }
    }

    val shape = remember(matrixItem) {
        when (matrixItem) {
            is MatrixItem.SpaceItem -> RoundedCornerShape(dimensionConverter.dpToPx(8))
            else -> CircleShape
        }
    }

    GlideSubcomposition(
            model = resolvedUrl,
            modifier = modifier.clip(shape),
    ) {
        when (state) {
            is RequestState.Failure,
            is RequestState.Loading -> {
                DefaultPlaceholder(
                        modifier = modifier,
                        matrixItem = matrixItem
                )
            }
            is RequestState.Success -> {
                Image(
                        painter = painter,
                        contentDescription = AvatarHelper.getContentDescription(stringProvider, matrixItem),
                        modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun DefaultPlaceholder(
        modifier: Modifier = Modifier,
        matrixItem: MatrixItem,
        userInRoomInformation: MatrixItemColorProvider.UserInRoomInformation? = null,
) {
    val matrixItemColorProvider = LocalMatrixItemColorProvider.current
    val dimensionConverter = LocalDimensionConverter.current

    val avatarColor = matrixItemColorProvider.getColor(matrixItem, userInRoomInformation)
    val shape = remember(matrixItem) {
        when (matrixItem) {
            is MatrixItem.SpaceItem -> RoundedCornerShape(dimensionConverter.dpToPx(8))
            else -> CircleShape
        }
    }

    Box(
            modifier = modifier
                    .clip(shape)
                    .background(Color(avatarColor)),
            contentAlignment = Alignment.Center
    ) {
        Text(
                text = matrixItem.firstLetterOfDisplayName(),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp
        )
    }
}
