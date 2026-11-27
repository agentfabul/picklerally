package com.picklerally.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseUser
import com.picklerally.app.R
import com.picklerally.app.data.HistorySource
import com.picklerally.app.data.LeaderboardRow
import com.picklerally.app.data.MatchHistoryItem
import com.picklerally.app.data.StatsRepository
import com.picklerally.app.data.UserProfileStats
import com.picklerally.app.data.unwrapFirestoreMessage
import com.picklerally.app.game.CharacterId
import com.picklerally.app.game.CourtSide
import com.picklerally.app.game.MatchRecordMode
import com.picklerally.app.game.characterPalette
import com.picklerally.app.ui.auth.GoogleAuthManager
import com.picklerally.app.ui.components.CharacterPortrait
import com.picklerally.app.ui.theme.AmberGold
import com.picklerally.app.ui.theme.BorderSlate
import com.picklerally.app.ui.theme.LimeAccent
import com.picklerally.app.ui.theme.NavyCard
import com.picklerally.app.ui.theme.NavyDeep
import com.picklerally.app.ui.theme.NavyElevated
import com.picklerally.app.ui.theme.NavySelected
import com.picklerally.app.ui.theme.NavySurface
import com.picklerally.app.ui.theme.OrangeAccent
import com.picklerally.app.ui.theme.RoseDarkBg
import com.picklerally.app.ui.theme.RoseFault
import com.picklerally.app.ui.theme.TextPrimary
import com.picklerally.app.ui.theme.TextSecondary
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private enum class HubTab(val label: String) {
    PROFILE("Profile"),
    HISTORY("History"),
    LEADERBOARD("Ranks"),
}

data class StatsHubUiState(
    val loading: Boolean = true,
    val profile: UserProfileStats? = null,
    val history: List<MatchHistoryItem> = emptyList(),
    val leaderboard: List<LeaderboardRow> = emptyList(),
    val errorMessage: String? = null,
    val nameSaving: Boolean = false,
    val nameSavedBanner: Boolean = false,
)

class StatsHubViewModel(
    private val authUser: FirebaseUser?,
    private val playerChar: CharacterId,
    private val repository: StatsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(StatsHubUiState())
    val uiState: StateFlow<StatsHubUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, errorMessage = null)
            val profileDef = async { repository.fetchUserProfile(authUser, playerChar) }
            val historyDef = async { repository.fetchMatchHistory(authUser) }
            val boardDef = async { repository.fetchLeaderboard(authUser, playerChar) }

            val profileRes = profileDef.await()
            val historyRes = historyDef.await()
            val boardRes = boardDef.await()

            val firstFailure = profileRes.exceptionOrNull()
                ?: historyRes.exceptionOrNull()
                ?: boardRes.exceptionOrNull()

            _uiState.value = _uiState.value.copy(
                loading = false,
                profile = profileRes.getOrNull() ?: _uiState.value.profile,
                history = historyRes.getOrNull() ?: _uiState.value.history,
                leaderboard = boardRes.getOrNull() ?: _uiState.value.leaderboard,
                errorMessage = firstFailure?.let {
                    unwrapFirestoreMessage(it, "Could not sync all cloud records.")
                },
            )
        }
    }

    fun saveDisplayName(rawName: String, onSaved: (String) -> Unit) {
        val trimmed = rawName.trim()
        if (trimmed.isEmpty()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Display name cannot be empty.")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(nameSaving = true, errorMessage = null)
            val res = repository.updateUserDisplayName(authUser, trimmed, playerChar)
            res.onSuccess { saved ->
                val updatedProfile = _uiState.value.profile?.copy(displayName = saved)
                _uiState.value = _uiState.value.copy(
                    nameSaving = false,
                    nameSavedBanner = true,
                    profile = updatedProfile,
                )
                onSaved(saved)
                val board = repository.fetchLeaderboard(authUser, playerChar).getOrNull()
                if (board != null) {
                    _uiState.value = _uiState.value.copy(leaderboard = board)
                }
                delay(1600L)
                _uiState.value = _uiState.value.copy(nameSavedBanner = false)
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(
                    nameSaving = false,
                    errorMessage = unwrapFirestoreMessage(err, "Could not update display name."),
                )
            }
        }
    }
}

@Composable
fun StatsHubScreen(
    authUser: FirebaseUser?,
    playerChar: CharacterId,
    repository: StatsRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    val vmKey = authUser?.uid ?: "local-guest"
    key(vmKey, playerChar) {
        val vm: StatsHubViewModel = viewModel(
            key = "$vmKey-${playerChar.name}",
            factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return StatsHubViewModel(authUser, playerChar, repository) as T
                }
            },
        )
        StatsHubContent(
            authUser = authUser,
            vm = vm,
            onBack = onBack,
            modifier = modifier,
        )
    }
}

@Composable
private fun StatsHubContent(
    authUser: FirebaseUser?,
    vm: StatsHubViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var tab by remember { mutableStateOf(HubTab.PROFILE) }
    var nameInput by remember { mutableStateOf("") }
    var authBusy by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.profile?.displayName) {
        val loaded = state.profile?.displayName
        if (!loaded.isNullOrBlank() && nameInput.isBlank()) {
            nameInput = loaded
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NavySurface)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("stats_back_button"),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, BorderSlate),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = NavyCard,
                        contentColor = TextPrimary,
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.back), style = MaterialTheme.typography.labelLarge)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Player Stats & Ranks",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                    )
                    Text(
                        text = if (authUser != null) {
                            "Firestore Cloud Profile"
                        } else {
                            "Local Profile (Sign in to sync)"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                    )
                }

                OutlinedButton(
                    onClick = { vm.refresh() },
                    enabled = !state.loading,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("stats_refresh_button"),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, BorderSlate),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = NavyCard,
                        contentColor = TextPrimary,
                    ),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    if (state.loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = LimeAccent,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Refresh stats",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            HorizontalDivider(color = Color(0xFF1E293B))
            Spacer(Modifier.height(10.dp))

            // Account Banner (Sign in with Google / Sign out)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = NavyCard,
                border = BorderStroke(1.dp, Color(0xFF1E293B)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (authUser == null) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Sync with Google Account",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextPrimary,
                            )
                            Text(
                                text = authError ?: "Save matches and climb public ranks",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = if (authError != null) RoseFault else TextSecondary,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                authBusy = true
                                authError = null
                                GoogleAuthManager.onGoogleSignInClicked(
                                    context = context,
                                    credentialManager = credentialManager,
                                    scope = scope,
                                    onAuthSuccess = { authBusy = false },
                                    onAuthError = { msg ->
                                        authBusy = false
                                        authError = msg
                                    },
                                    onAuthCancelled = { authBusy = false },
                                )
                            },
                            enabled = !authBusy,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("stats_google_sign_in_button"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = LimeAccent,
                                contentColor = NavyDeep,
                            ),
                        ) {
                            if (authBusy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = NavyDeep,
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Text(
                                    text = stringResource(R.string.sign_in_with_google),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    } else {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = authUser.displayName ?: authUser.email ?: "Signed in",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = authUser.email ?: "Google Account connected",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = LimeAccent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                GoogleAuthManager.signOut(credentialManager, scope)
                            },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("stats_sign_out_button"),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, BorderSlate),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.sign_out), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Segmented Tabs
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = NavyDeep,
                border = BorderStroke(1.dp, Color(0xFF1E293B)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    HubTabButton(
                        label = "Profile",
                        icon = Icons.Filled.Person,
                        selected = tab == HubTab.PROFILE,
                        activeColor = TextPrimary,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("stats_tab_profile"),
                        onClick = { tab = HubTab.PROFILE },
                    )
                    HubTabButton(
                        label = "History",
                        icon = Icons.Filled.History,
                        selected = tab == HubTab.HISTORY,
                        activeColor = TextPrimary,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("stats_tab_history"),
                        onClick = { tab = HubTab.HISTORY },
                    )
                    HubTabButton(
                        label = "Ranks",
                        icon = Icons.Filled.EmojiEvents,
                        selected = tab == HubTab.LEADERBOARD,
                        activeColor = LimeAccent,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("stats_tab_ranks"),
                        onClick = { tab = HubTab.LEADERBOARD },
                    )
                }
            }

            if (state.errorMessage != null) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = RoseDarkBg.copy(alpha = 0.65f),
                    border = BorderStroke(1.dp, RoseFault.copy(alpha = 0.45f)),
                ) {
                    Text(
                        text = state.errorMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFECDD3),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Tab body
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when (tab) {
                    HubTab.PROFILE -> {
                        val profile = state.profile
                        if (profile != null) {
                            val winRate = if (profile.matchesPlayed > 0) {
                                ((profile.wins.toFloat() / profile.matchesPlayed.toFloat()) * 100f).toInt()
                            } else {
                                0
                            }
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = NavyCard,
                                border = BorderStroke(1.dp, Color(0xFF1E293B)),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        CharacterPortrait(characterId = profile.characterId, size = 54.dp)
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "ACTIVE FIGHTER · ${characterPalette(profile.characterId).name.uppercase()}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = TextSecondary,
                                            )
                                            Text(
                                                text = profile.displayName,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = TextPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                text = "Rating ${profile.rating}",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = LimeAccent,
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        OutlinedTextField(
                                            value = nameInput,
                                            onValueChange = { if (it.length <= 32) nameInput = it },
                                            singleLine = true,
                                            placeholder = { Text("Player name") },
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("display_name_input"),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedContainerColor = NavyDeep,
                                                unfocusedContainerColor = NavyDeep,
                                                focusedBorderColor = LimeAccent,
                                                unfocusedBorderColor = BorderSlate,
                                                focusedTextColor = TextPrimary,
                                                unfocusedTextColor = TextPrimary,
                                            ),
                                            shape = RoundedCornerShape(10.dp),
                                        )
                                        Button(
                                            onClick = {
                                                vm.saveDisplayName(nameInput) { saved ->
                                                    nameInput = saved
                                                }
                                            },
                                            enabled = !state.nameSaving,
                                            modifier = Modifier
                                                .heightIn(min = 52.dp)
                                                .testTag("save_display_name_button"),
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = LimeAccent,
                                                contentColor = NavyDeep,
                                            ),
                                        ) {
                                            if (state.nameSavedBanner) {
                                                Icon(
                                                    imageVector = Icons.Filled.Check,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Text("Saved", style = MaterialTheme.typography.labelLarge)
                                            } else {
                                                Text("Save", style = MaterialTheme.typography.labelLarge)
                                            }
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                StatMetricCard(
                                    label = "Record (W – L)",
                                    value = "${profile.wins} – ${profile.losses}",
                                    valueColor = TextPrimary,
                                    modifier = Modifier.weight(1f),
                                )
                                StatMetricCard(
                                    label = "Win Rate",
                                    value = "$winRate%",
                                    valueColor = LimeAccent,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                StatMetricCard(
                                    label = "Best Rally",
                                    value = "${profile.bestRally} shots",
                                    valueColor = TextPrimary,
                                    modifier = Modifier.weight(1f),
                                )
                                StatMetricCard(
                                    label = "Points Scored",
                                    value = profile.pointsScored.toString(),
                                    valueColor = OrangeAccent,
                                    modifier = Modifier.weight(1f),
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = NavyDeep,
                                border = BorderStroke(1.dp, Color(0xFF1E293B)),
                            ) {
                                Text(
                                    text = "Completed 1v1 and 2v2 matches save to Firestore /matches and update your profile & public /leaderboard rankings.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(12.dp),
                                )
                            }
                        } else if (state.loading) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(color = LimeAccent)
                            }
                        }
                    }

                    HubTab.HISTORY -> {
                        if (state.history.isEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = NavyCard,
                                border = BorderStroke(1.dp, Color(0xFF1E293B)),
                            ) {
                                Text(
                                    text = "No finished matches recorded yet. Finish a 1v1 or 2v2 game to log it here.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                )
                            }
                        } else {
                            state.history.forEach { item ->
                                val won = item.winnerSide == CourtSide.LOWER
                                val modeLabel = when (item.mode) {
                                    MatchRecordMode.DOUBLES_2V2 -> "Online 2v2"
                                    MatchRecordMode.SOLO_2V2 -> "2v2 Doubles"
                                    MatchRecordMode.SINGLES_1V1 -> "Online 1v1"
                                    MatchRecordMode.SOLO_1V1 -> "Solo 1v1"
                                }
                                val statusLabel = when {
                                    item.source == HistorySource.LOCAL -> "Local"
                                    item.validated -> "Verified"
                                    else -> "Cloud Synced"
                                }
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = NavyCard,
                                    border = BorderStroke(1.dp, Color(0xFF1E293B)),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            CharacterPortrait(
                                                characterId = item.opponentCharacter,
                                                size = 38.dp,
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    Text(
                                                        text = if (won) "Win" else "Loss",
                                                        style = MaterialTheme.typography.labelLarge,
                                                        color = if (won) LimeAccent else Color(0xFFFDA4AF),
                                                    )
                                                    Text(
                                                        text = "· $modeLabel vs ${characterPalette(item.opponentCharacter).name}",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = TextPrimary,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                }
                                                Text(
                                                    text = "Rally ${item.bestRally} · $statusLabel",
                                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                                    color = TextSecondary,
                                                )
                                            }
                                        }
                                        Text(
                                            text = "${item.scoreLower} – ${item.scoreUpper}",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = TextPrimary,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HubTab.LEADERBOARD -> {
                        if (state.leaderboard.isEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = NavyCard,
                                border = BorderStroke(1.dp, Color(0xFF1E293B)),
                            ) {
                                Text(
                                    text = "No ranked players on the board yet. Sign in and finish a match to claim #1.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                )
                            }
                        } else {
                            state.leaderboard.forEachIndexed { idx, row ->
                                val isSelf = if (authUser != null) {
                                    row.uid == authUser.uid
                                } else {
                                    row.uid == "local-player"
                                }
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelf) NavySelected else NavyCard,
                                    border = BorderStroke(
                                        width = 1.dp,
                                        color = if (isSelf) LimeAccent.copy(alpha = 0.6f) else Color(0xFF1E293B),
                                    ),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Box(
                                                modifier = Modifier.width(28.dp),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                if (idx == 0) {
                                                    Icon(
                                                        imageVector = Icons.Filled.WorkspacePremium,
                                                        contentDescription = "Rank 1",
                                                        tint = AmberGold,
                                                        modifier = Modifier.size(18.dp),
                                                    )
                                                } else {
                                                    Text(
                                                        text = "#${idx + 1}",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = TextSecondary,
                                                    )
                                                }
                                            }
                                            CharacterPortrait(characterId = row.characterId, size = 34.dp)
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = row.displayName,
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = TextPrimary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                Text(
                                                    text = "${row.wins}W – ${row.losses}L · Best ${row.bestRally}",
                                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                                    color = TextSecondary,
                                                )
                                            }
                                        }
                                        Text(
                                            text = row.rating.toString(),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = LimeAccent,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HubTabButton(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    activeColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) NavyElevated else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) activeColor else TextSecondary,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) activeColor else TextSecondary,
            )
        }
    }
}

@Composable
private fun StatMetricCard(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = NavyCard,
        border = BorderStroke(1.dp, Color(0xFF1E293B)),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = valueColor,
            )
        }
    }
}
