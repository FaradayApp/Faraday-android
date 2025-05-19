/*
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package im.vector.app.features.settings.connectionmethod.compose

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import im.vector.app.core.settings.connectionmethods.DefaultValidateProxyUseCase
import im.vector.app.core.settings.connectionmethods.ProxyValidationError
import im.vector.app.core.settings.connectionmethods.ProxyValidationException
import im.vector.app.core.settings.connectionmethods.onion.TorEvent
import im.vector.app.core.settings.connectionmethods.onion.TorEventListener
import im.vector.app.core.settings.connectionmethods.onion.TorService
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.matrix.android.sdk.api.settings.LightweightSettingsStorage
import org.matrix.android.sdk.api.util.ConnectionType
import org.matrix.android.sdk.api.util.ProxyType
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ConnectionSettingsViewModel @Inject constructor(
        private val lightweightSettingsStorage: LightweightSettingsStorage,
        private val torService: TorService,
        torEventListener: TorEventListener,
        private val validateProxyUseCase: DefaultValidateProxyUseCase,
) : ViewModel() {
    private val initialStateCache = mutableMapOf<ConnectionType, ConnectionSettingsUiState>()

    private val torEventFlow = torEventListener.torEventLiveData
            .asFlow()
            .mapNotNull { it.getContentIfNotHandled() }
    private val _uiState = MutableStateFlow<ConnectionSettingsUiState>(ConnectionSettingsUiState.ConfigureMatrix.Plain)
    val uiState: StateFlow<ConnectionSettingsUiState> = _uiState.asStateFlow()

    private val _uiEvents = MutableSharedFlow<ConnectionSettingsUiEvent>(replay = 0)
    val uiEvents = _uiEvents.asSharedFlow()

    init {
        cacheInitialStates()

        _uiState.value = initialStateCache[lightweightSettingsStorage.getConnectionType()]
                ?: ConnectionSettingsUiState.ConfigureMatrix.Plain

        viewModelScope.launch {
            torEventFlow.collect { torEvent ->
                updateTorState { current ->
                    when (torEvent) {
                        TorEvent.ConnectionEstablished -> {
                            onConnected(ConnectionType.ONION, false)
                            current.copy(isLoading = false)
                        }
                        TorEvent.ConnectionFailed -> current.copy(isLoadingFailed = true, isLoading = false)
                        is TorEvent.TorLogEvent -> current.copy(loadingDescription = torEvent.message)
                    }
                }
            }
        }
    }

    private fun cacheInitialStates() {
        val proxyType = lightweightSettingsStorage.getProxyType()
        val matrixState = if (proxyType == ProxyType.NO_PROXY) {
            ConnectionSettingsUiState.ConfigureMatrix.Plain
        } else {
            MatrixWithProxy(
                    proxyType = proxyType,
                    host = lightweightSettingsStorage.getProxyHost(),
                    port = lightweightSettingsStorage.getProxyPort().toString(),
                    username = lightweightSettingsStorage.getProxyUsername(),
                    password = lightweightSettingsStorage.getProxyPassword()
            )
        }
        initialStateCache[ConnectionType.MATRIX] = matrixState

        initialStateCache[ConnectionType.ONION] = ConnectionSettingsUiState.ConfigureTor(
                bridges = lightweightSettingsStorage.getTorBridge().orEmpty()
        )
    }

    private var torCancelled = false

    fun setConnectionType(type: ConnectionType) {
        _uiState.update { current ->
            when (type) {
                ConnectionType.MATRIX -> when (current) {
                    is ConnectionSettingsUiState.ConfigureMatrix -> current
                    else -> ConnectionSettingsUiState.ConfigureMatrix.Plain
                }

                ConnectionType.ONION -> when (current) {
                    is ConnectionSettingsUiState.ConfigureTor -> current
                    else -> ConnectionSettingsUiState.ConfigureTor(bridges = "")
                }

                ConnectionType.I2P -> current
            }
        }
    }

    fun toggleProxy(enabled: Boolean) {
        _uiState.update { current ->
            when (current) {
                is ConnectionSettingsUiState.ConfigureMatrix -> {
                    if (enabled) {
                        if (current is MatrixWithProxy) current
                        else MatrixWithProxy()
                    } else {
                        ConnectionSettingsUiState.ConfigureMatrix.Plain
                    }
                }

                else -> current
            }
        }
    }

    private inline fun updateProxyState(
            crossinline function: (MatrixWithProxy) -> MatrixWithProxy
    ) {
        _uiState.update { current ->
            if (current is MatrixWithProxy) {
                function(current)
            } else current
        }
    }

    private inline fun updateTorState(
            crossinline function: (ConnectionSettingsUiState.ConfigureTor) -> ConnectionSettingsUiState.ConfigureTor
    ) {
        _uiState.update { current ->
            if (current is ConnectionSettingsUiState.ConfigureTor) {
                function(current)
            } else current
        }
    }

    fun onProxyTypeChanged(proxyType: ProxyType) {
        updateProxyState { it.copy(proxyType = proxyType) }
    }

    fun onProxyHostChanged(host: String) {
        updateProxyState { it.copy(host = host) }
    }

    fun onProxyPortChanged(port: String) {
        updateProxyState { it.copy(port = port) }
    }

    fun onProxyUsernameChanged(username: String) {
        updateProxyState { it.copy(username = username) }
    }

    fun onProxyPasswordChanged(password: String) {
        updateProxyState { it.copy(password = password) }
    }

    fun onBridgesChanged(bridges: String) {
        updateTorState { current ->
            current.copy(bridges = bridges)
        }
    }

    fun onSave() {
        when (val state = _uiState.value) {
            is ConnectionSettingsUiState.ConfigureMatrix -> tryInitMatrixConnection(state)

            is ConnectionSettingsUiState.ConfigureTor -> {
                tryInitTorConnection()
            }
        }
    }

    private fun tryInitTorConnection() {
        torCancelled = false

        val (hasNewBridge, newBridge) = getNewBridgeOrNull()

        if (hasNewBridge) {
            lightweightSettingsStorage.setTorBridge(newBridge)
            onConnected(ConnectionType.ONION, true)
        } else {
            when (torService.isProxyRunning) {
                true -> onConnected(ConnectionType.ONION, false)
                false -> {
                    // TODO: maybe unreachable code
                    torService.switchTorPrefState(true)
                    updateTorState { it.copy(isLoading = true) }
                }
            }
        }
    }

    private fun tryInitMatrixConnection(state: ConnectionSettingsUiState.ConfigureMatrix) {
        cancelTorInit()
        when (state.proxyEnabled) {
            true -> {
                if (state !is MatrixWithProxy) error("Invalid state")
                if (state.proxyType == ProxyType.NO_PROXY) {
                    onConnected(ConnectionType.MATRIX)
                    return
                }

                validateProxyUseCase.validate(
                        type = state.proxyType,
                        host = state.host,
                        port = state.port,
                        username = state.username,
                        password = state.password
                ).onSuccess {
                    saveProxy(
                            type = state.proxyType,
                            host = state.host,
                            port = state.port,
                            username = state.username,
                            password = state.password
                    )
                    onConnected(ConnectionType.MATRIX)
                }.onFailure { exception ->
                    if (exception !is ProxyValidationException) {
                        viewModelScope.launch {
                            exception.message?.let {
                                _uiEvents.emit(ConnectionSettingsUiEvent.UnknownError(it))
                            }
                        }
                    }

                    var hostError: String? = null
                    var portError: String? = null
                    var usernameError: String? = null
                    var passwordError: String? = null
                    (exception as? ProxyValidationException)?.errors?.forEach { error ->
                        when (error) {
                            is ProxyValidationError.InvalidHostError ->
                                hostError = error.message

                            is ProxyValidationError.InvalidPasswordError ->
                                passwordError = error.message

                            is ProxyValidationError.InvalidPortError ->
                                portError = error.message

                            is ProxyValidationError.InvalidUsernameError ->
                                usernameError = error.message
                        }
                    }

                    updateProxyState {
                        it.copy(
                                hostError = hostError,
                                portError = portError,
                                usernameError = usernameError,
                                passwordError = passwordError,
                        )
                    }
                }
            }

            false -> {
                disableProxy()
                onConnected(ConnectionType.MATRIX)
            }
        }
    }

    private fun onConnected(type: ConnectionType, needRestart: Boolean = false) {
        viewModelScope.launch {
            if (type != ConnectionType.ONION && torService.isProxyRunning) {
                torService.switchTorPrefState(false)
            }
            lightweightSettingsStorage.setConnectionType(type)
            _uiEvents.emit(
                    ConnectionSettingsUiEvent.Connected(needRestart)
            )
        }
    }

    private fun saveProxy(type: ProxyType, host: String, port: String, username: String, password: String) {
        lightweightSettingsStorage.setProxyPort(port.toInt())
        lightweightSettingsStorage.setProxyHost(host)
        lightweightSettingsStorage.setProxyUsername(username)
        lightweightSettingsStorage.setProxyPassword(password)
        lightweightSettingsStorage.setProxyType(type)
    }

    private fun disableProxy() {
        lightweightSettingsStorage.setProxyType(ProxyType.NO_PROXY)
        lightweightSettingsStorage.setProxyHost("")
        lightweightSettingsStorage.setProxyPort(0)
        lightweightSettingsStorage.setProxyUsername("")
        lightweightSettingsStorage.setProxyPassword("")
    }

    private fun cancelTorInit() {
        torService.switchTorPrefState(false)
        disableProxy()
        torCancelled = true
    }

    private fun getNewBridgeOrNull(): Pair<Boolean, String?> {
        val newBridge = (_uiState.value as? ConnectionSettingsUiState.ConfigureTor)?.bridges?.takeIf { it.isNotBlank() }
        val oldBridge = lightweightSettingsStorage.getTorBridge().takeIf { !it.isNullOrBlank() }
        Timber.d("New bridge: $newBridge\nOld bridge: $oldBridge\nHas new bridge: ${oldBridge != newBridge}")

        return (newBridge != oldBridge) to newBridge
    }
}

typealias MatrixWithProxy = ConnectionSettingsUiState.ConfigureMatrix.WithProxy

sealed class ConnectionSettingsUiState(val type: ConnectionType) {
    sealed class ConfigureMatrix(val proxyEnabled: Boolean) : ConnectionSettingsUiState(ConnectionType.MATRIX) {
        data object Plain : ConfigureMatrix(false)
        data class WithProxy(
                val proxyType: ProxyType = ProxyType.NO_PROXY,
                val host: String = "",
                val hostError: String? = null,
                val port: String = "",
                val portError: String? = null,
                val username: String = "",
                val usernameError: String? = null,
                val password: String = "",
                val passwordError: String? = null,
        ) : ConfigureMatrix(true)
    }

    data class ConfigureTor(
            val isLoadingFailed: Boolean = false,
            val isLoading: Boolean = false,
            val loadingDescription: String = "",
            val bridges: String = ""// TODO: make list of bridges
    ) : ConnectionSettingsUiState(ConnectionType.ONION)
}

sealed interface ConnectionSettingsUiEvent {
    data class Connected(val needRestart: Boolean = false) : ConnectionSettingsUiEvent
    data class UnknownError(val message: String) : ConnectionSettingsUiEvent
}
