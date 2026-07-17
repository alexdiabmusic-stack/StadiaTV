package com.stadiatv.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stadiatv.BuildConfig
import com.stadiatv.core.database.PlaylistSourceEntity
import com.stadiatv.core.database.SourceDao
import com.stadiatv.core.model.SourceStatus
import com.stadiatv.core.model.SourceType
import com.stadiatv.core.security.CredentialStore
import com.stadiatv.core.sync.AdapterResult
import com.stadiatv.core.sync.SyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.URI
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

data class OnboardingUiState(
    val mode: SourceType = SourceType.M3U,
    val displayName: String = "",
    val playlistUrl: String = "",
    val epgUrl: String = "",
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val authorized: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val syncedChannels: Int? = null,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val sourceDao: SourceDao,
    private val credentialStore: CredentialStore,
    private val syncManager: SyncManager,
) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    fun update(transform: OnboardingUiState.() -> OnboardingUiState) {
        _state.update { it.transform().copy(error = null) }
    }

    fun saveAndSync(onDone: () -> Unit) {
        val state = _state.value
        val validationError = validate(state)
        if (validationError != null) {
            _state.update { it.copy(error = validationError) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }
            val now = Instant.now()
            val id = "source-${UUID.randomUUID()}"
            val source = PlaylistSourceEntity(
                id = id,
                displayName = state.displayName.ifBlank { if (state.mode == SourceType.M3U) "M3U playlist" else "Xtream account" },
                type = state.mode,
                baseUrl = if (state.mode == SourceType.XTREAM) normalizeServer(state.serverUrl) else null,
                playlistUrl = if (state.mode == SourceType.M3U) state.playlistUrl.trim() else null,
                epgUrl = state.epgUrl.ifBlank { null },
                enabled = true,
                status = SourceStatus.IDLE,
                lastAttemptAt = null,
                lastSuccessfulSyncAt = null,
                lastErrorCode = null,
                createdAt = now,
                updatedAt = now,
            )
            sourceDao.upsert(source)
            if (state.mode == SourceType.XTREAM) {
                credentialStore.putSecret(id, "username", state.username)
                credentialStore.putSecret(id, "password", state.password)
            }
            when (val result = syncManager.syncNow(id)) {
                is AdapterResult.Success -> {
                    _state.update { it.copy(saving = false, syncedChannels = result.value) }
                    onDone()
                }
                is AdapterResult.Failure -> _state.update { it.copy(saving = false, error = result.redactedMessage) }
            }
        }
    }

    private fun validate(state: OnboardingUiState): String? {
        if (!state.authorized) return "Confirm that you are authorized to access this content."
        if (state.displayName.length > 80) return "Display name is too long."
        return when (state.mode) {
            SourceType.M3U -> validateUrl(state.playlistUrl, required = true)
            SourceType.XTREAM -> validateUrl(state.serverUrl, required = true) ?: if (state.username.isBlank() || state.password.isBlank()) "Username and password are required." else null
        }
    }

    private fun validateUrl(value: String, required: Boolean): String? {
        val url = value.trim()
        if (required && url.isBlank()) return "URL is required."
        val scheme = runCatching { URI(url).scheme?.lowercase() }.getOrNull() ?: return "Enter a valid URL."
        if (scheme == "http" && !BuildConfig.ALLOW_USER_HTTP_SOURCES) return "HTTP sources are disabled in the secure build."
        if (scheme !in setOf("https", "http")) return "Only HTTP and HTTPS URLs are supported."
        return null
    }

    private fun normalizeServer(value: String): String = value.trim().trimEnd('/')
}
