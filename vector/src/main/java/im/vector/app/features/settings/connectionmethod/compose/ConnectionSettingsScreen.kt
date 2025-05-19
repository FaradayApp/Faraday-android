/*
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package im.vector.app.features.settings.connectionmethod.compose

import android.annotation.SuppressLint
import android.widget.Switch
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import im.vector.app.features.themes.compose.FaradayTheme
import im.vector.lib.strings.CommonStrings
import org.matrix.android.sdk.api.extensions.orFalse
import org.matrix.android.sdk.api.util.ConnectionType
import org.matrix.android.sdk.api.util.ProxyType

data class TorLoadingState(
        val isLoading: Boolean,
        val hasError: Boolean,
        val loadingDescription: String?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionSettingsTopBar(onBackClick: () -> Unit) {
    Surface(shadowElevation = 3.dp) {
        TopAppBar(
                title = {
                    Text(
                            text = stringResource(CommonStrings.settings_connection_method),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                ),
        )
    }
}


@Composable
fun ConnectionChooseScreen(viewModel: ConnectionSettingsViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    var torLoadingState: TorLoadingState by remember {
        mutableStateOf(
            TorLoadingState(
                    isLoading = (uiState as? ConnectionSettingsUiState.ConfigureTor)
                            ?.isLoading.orFalse(),
                    hasError = (uiState as? ConnectionSettingsUiState.ConfigureTor)
                            ?.isLoadingFailed.orFalse(),
                    loadingDescription = (uiState as? ConnectionSettingsUiState.ConfigureTor)
                            ?.loadingDescription,
            )
        )
    }

    LaunchedEffect(uiState) {
        if (uiState is ConnectionSettingsUiState.ConfigureTor) {
            torLoadingState = TorLoadingState(
                    isLoading = (uiState as? ConnectionSettingsUiState.ConfigureTor)
                            ?.isLoading.orFalse(),
                    hasError = (uiState as? ConnectionSettingsUiState.ConfigureTor)
                            ?.isLoadingFailed.orFalse(),
                    loadingDescription = (uiState as? ConnectionSettingsUiState.ConfigureTor)
                            ?.loadingDescription,
            )
        }
    }

    Column(Modifier.verticalScroll(scrollState)) {
        ConnectionRadioGroup(
                selectedType = uiState.type,
                onSelected = viewModel::setConnectionType,
                hasTorLoading = torLoadingState.isLoading,
                hasTorError = torLoadingState.hasError,
                torLoadingDescription = torLoadingState.loadingDescription,
        )

        AnimatedContent(
                targetState = uiState.type,
                transitionSpec = {
                    fadeIn(tween(250)) + slideInVertically { it / 3 } togetherWith
                            fadeOut(tween(200)) + slideOutVertically { -it / 3 }
                },
                label = "ConnectionSettingsAnimatedContent"
        ) { type ->
            when (type) {
                ConnectionType.MATRIX -> {
                    val state = uiState as? ConnectionSettingsUiState.ConfigureMatrix
                            ?: return@AnimatedContent
                    Column {
                        ProxyToggle(
                                isEnabled = state.proxyEnabled,
                                onSwitch = viewModel::toggleProxy
                        )
                        if (state.proxyEnabled && state is MatrixWithProxy) {
                            ProxySettingsForm(
                                    state = state,
                                    onTypeChanged = viewModel::onProxyTypeChanged,
                                    onHostChanged = viewModel::onProxyHostChanged,
                                    onPortChanged = viewModel::onProxyPortChanged,
                                    onUsernameChanged = viewModel::onProxyUsernameChanged,
                                    onPasswordChanged = viewModel::onProxyPasswordChanged
                            )
                        }
                    }
                }

                ConnectionType.ONION -> {
                    val state = uiState as? ConnectionSettingsUiState.ConfigureTor
                            ?: return@AnimatedContent
                    TorPreferenceSettings(
                            state = state,
                            onBridgesChanged = viewModel::onBridgesChanged
                    )
                }

                ConnectionType.I2P -> {}
            }
        }

        Button(
                onClick = viewModel::onSave,
                shape = RoundedCornerShape(10),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp, bottom = 40.dp, start = 16.dp, end = 16.dp)
                        .height(55.dp)
        ) {
            Text(
                    text = stringResource(CommonStrings.action_save),
                    fontSize = 16.sp,
            )
        }
    }
}

@Composable
internal fun ConnectionRadioGroup(
        selectedType: ConnectionType,
        onSelected: (ConnectionType) -> Unit,
        hasTorError: Boolean = false,
        hasTorLoading: Boolean = false,
        torLoadingDescription: String? = null,
) {
    Column {
        Text(
                text = stringResource(CommonStrings.settings_choose_connection_method),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.padding(start = 20.dp, top = 24.dp)
        )
        Column(
                Modifier
                        .selectableGroup()
                        .padding(start = 20.dp, top = 24.dp, end = 20.dp)
        ) {
            ConnectionRadioButton(
                    text = stringResource(CommonStrings.settings_matrix_protocol),
                    isSelected = selectedType == ConnectionType.MATRIX,
                    onSelected = { onSelected(ConnectionType.MATRIX) },
                    isFirst = true,
            )
            ConnectionRadioButton(
                    text = stringResource(CommonStrings.settings_onion_routing_protocol),
                    isSelected = selectedType == ConnectionType.ONION,
                    onSelected = { onSelected(ConnectionType.ONION) },
                    isError = hasTorError,
                    isLoading = hasTorLoading,
                    loadingDescription = torLoadingDescription,
            )
            ConnectionRadioButton(
                    text = stringResource(CommonStrings.settings_i2p_network),
                    isSelected = selectedType == ConnectionType.I2P,
                    onSelected = { onSelected(ConnectionType.I2P) },
                    isEnabled = false
            )
        }
    }
}

@Composable
@SuppressLint("UseSwitchCompatOrMaterialCode")
internal fun ProxyToggle(
        isEnabled: Boolean = false,
        onSwitch: (Boolean) -> Unit = {},
) {
    Row(
            modifier = Modifier
                    .padding(top = 20.dp, start = 20.dp, end = 20.dp)
                    .fillMaxWidth()
                    .selectable(
                            selected = isEnabled,
                            onClick = { onSwitch(!isEnabled) },
                            role = Role.Switch
                    ),
            verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Text(
                    text = stringResource(CommonStrings.settings_use_proxy_server),
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
            )
            Row {
                Spacer(modifier = Modifier.weight(1f))
                AndroidView(
                        factory = { context ->
                            Switch(context).apply {
                                isChecked = isEnabled
                                setOnCheckedChangeListener { _, isCheckedNew ->
                                    onSwitch(isCheckedNew)
                                }
                            }
                        },
                        update = { view ->
                            view.isChecked = isEnabled
                            view.setOnCheckedChangeListener { _, isCheckedNew ->
                                onSwitch(isCheckedNew)
                            }
                        }
                )
            }
        }
    }
}

@Composable
internal fun TorPreferenceSettings(
        state: ConnectionSettingsUiState.ConfigureTor,
        onBridgesChanged: (String) -> Unit
) {
    OutlinedTextField(
            value = state.bridges,
            onValueChange = onBridgesChanged,
            label = { Text(stringResource(CommonStrings.settings_connection_method_bridge_hint)) },
            modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp, start = 20.dp, end = 20.dp)
    )
}

@Composable
internal fun ProxySettingsForm(
        state: ConnectionSettingsUiState.ConfigureMatrix.WithProxy,
        onTypeChanged: (ProxyType) -> Unit,
        onHostChanged: (String) -> Unit,
        onPortChanged: (String) -> Unit,
        onUsernameChanged: (String) -> Unit,
        onPasswordChanged: (String) -> Unit,
) {
    var dropdownExpanded: Boolean by remember { mutableStateOf(false) }

    Column(
            modifier = Modifier
                    .padding(top = 20.dp, start = 20.dp, end = 20.dp)
    ) {
        Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                    text = stringResource(CommonStrings.settings_connection_method_user_proxy_type),
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f)
            )
            Box {
                TextButton(onClick = { dropdownExpanded = true }) {
                    Text(state.proxyType.toString())
                }
                DropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false }
                ) {
                    ProxyType.entries.forEach {
                        DropdownMenuItem(
                                text = { Text(it.toString()) },
                                onClick = {
                                    onTypeChanged(it)
                                    dropdownExpanded = false
                                }
                        )
                    }
                }
            }
        }

        OutlinedTextField(
                value = state.host,
                onValueChange = onHostChanged,
                label = { Text(stringResource(CommonStrings.settings_connection_method_server_ip_address_hint)) },
                isError = state.hostError != null,
                supportingText = state.hostError?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
                value = state.port,
                onValueChange = onPortChanged,
                label = { Text(stringResource(CommonStrings.settings_connection_method_port_hint)) },
                isError = state.portError != null,
                supportingText = state.portError?.let { { Text(it) } },
                modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
        )

        Text(
                text = stringResource(CommonStrings.settings_connection_method_authentication),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 20.dp)
        )

        OutlinedTextField(
                value = state.username,
                onValueChange = onUsernameChanged,
                label = { Text(stringResource(CommonStrings.settings_connection_method_user_name_hint)) },
                isError = state.usernameError != null,
                supportingText = state.usernameError?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
                value = state.password,
                onValueChange = onPasswordChanged,
                label = { Text(stringResource(CommonStrings.settings_connection_method_password_hint)) },
                isError = state.passwordError != null,
                supportingText = state.passwordError?.let { { Text(it) } },
                modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
        )
    }
}

@Composable
internal fun ConnectionRadioButton(
        text: String,
        onSelected: () -> Unit,
        isSelected: Boolean = false,
        isFirst: Boolean = false,
        isEnabled: Boolean = true,
        isError: Boolean = false,
        isLoading: Boolean = false,
        loadingDescription: String? = null,
) {
    val shape = RoundedCornerShape(15)
    val interactionSource = remember { MutableInteractionSource() }
    val backgroundColor by animateColorAsState(
            targetValue = if (isSelected) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent,
            animationSpec = tween(durationMillis = 400),
            label = "radio_button_background"
    )

    val borderColor = if (isError) MaterialTheme.colorScheme.error
    else MaterialTheme.colorScheme.primary

    Row(
            modifier = Modifier
                    .fillMaxWidth()
                    .then(if (!isFirst) Modifier.padding(top = 12.dp) else Modifier)
                    .height(80.dp)
                    .clip(shape)
                    .border(1.dp, borderColor, shape)
                    .then(
                            if (isSelected) Modifier.background(backgroundColor, shape) else Modifier
                    )
                    .selectable(
                            selected = isSelected,
                            enabled = isEnabled,
                            onClick = onSelected,
                            role = Role.RadioButton,
                            indication = null,
                            interactionSource = interactionSource
                    )
                    .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
    ) {
        Crossfade(targetState = isSelected, animationSpec = tween(400)) {
            RadioButton(
                    selected = it,
                    onClick = null
            )
        }
        Column {
            Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(start = 16.dp)
            )
            if (isLoading) {
                LinearProgressIndicator(
                        modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp, start = 16.dp, end = 16.dp),
                )
                loadingDescription?.let { text ->
                    Text(
                            text = text,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp, start = 16.dp)
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun TopAppBarPreview() {
    FaradayTheme {
        ConnectionSettingsTopBar { }
    }
}
