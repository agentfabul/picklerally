package com.picklerally.app.ui.screens

import android.graphics.Paint
import android.graphics.Typeface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import androidx.credentials.CredentialManager
import com.google.firebase.auth.FirebaseUser
import com.picklerally.app.R
import com.picklerally.app.data.NearbyConnectionsManager
import com.picklerally.app.data.NearbyHostInfo
import com.picklerally.app.game.BOT_PROFILES
import com.picklerally.app.game.BotDifficulty
import com.picklerally.app.game.CharacterId
import com.picklerally.app.game.ScoringMode
import com.picklerally.app.game.characterPalette
import com.picklerally.app.ui.auth.GoogleAuthManager
import com.picklerally.app.ui.components.CharacterPortrait
import com.picklerally.app.ui.theme.BorderSlate
import com.picklerally.app.ui.theme.CourtApron
import com.picklerally.app.ui.theme.LimeAccent
import com.picklerally.app.ui.theme.LimeBright
import com.picklerally.app.ui.theme.NavyCard
import com.picklerally.app.ui.theme.NavyDeep
import com.picklerally.app.ui.theme.NavyElevated
import com.picklerally.app.ui.theme.NavySurface
import com.picklerally.app.ui.theme.OrangeAccent
import com.picklerally.app.ui.theme.RoseFault
import com.picklerally.app.ui.theme.TextMuted
import com.picklerally.app.ui.theme.TextPrimary
import com.picklerally.app.ui.theme.TextSecondary

enum class TitleModeTab(val label: String) {
    SOLO("Solo"),
    DOUBLES("2v2"),
    ONLINE("Online"),
    NEARBY("Nearby"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainMenuScreen(
    titleTab: TitleModeTab,
    scoringMode: ScoringMode,
    pointsToWin: Int,
    bestOf: Int,
    difficulty: BotDifficulty,
    muted: Boolean,
    profileName: String,
    playerChar: CharacterId,
    partnerChar: CharacterId,
    opponentChar: CharacterId,
    authUser: FirebaseUser?,
    nearbyHosts: List<NearbyHostInfo>,
    nearbyError: String?,
    nearbyHasPermissions: Boolean,
    onSaveProfileName: (String) -> Unit,
    onSelectTitleTab: (TitleModeTab) -> Unit,
    onSelectScoringMode: (ScoringMode) -> Unit,
    onSelectPointsToWin: (Int) -> Unit,
    onSelectBestOf: (Int) -> Unit,
    onSelectDifficulty: (BotDifficulty) -> Unit,
    onToggleMute: () -> Unit,
    onOpenStatsHub: () -> Unit,
    onOpenLearn: () -> Unit,
    onOpenCharacterSelect: () -> Unit,
    onStartSoloOrDoubles: () -> Unit,
    onStartNearbyDiscovery: () -> Unit,
    onStopNearbyDiscovery: () -> Unit,
    onHostNearby: () -> Unit,
    onJoinNearby: (NearbyHostInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var showSettingsSheet by remember { mutableStateOf(false) }
    var showProfileNameDialog by remember { mutableStateOf(false) }
    var pendingNearbyAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val hasCustomProfileName = profileName.isNotBlank() && profileName != "Local Player"
    var profileNameDraft by remember(profileName) {
        mutableStateOf(if (hasCustomProfileName) profileName else "")
    }
    var authLoading by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }
    var hasNearbyPerms by remember { mutableStateOf(nearbyHasPermissions) }
    var permissionDeniedNote by remember { mutableStateOf(false) }

    LaunchedEffect(nearbyHasPermissions) {
        hasNearbyPerms = nearbyHasPermissions
    }

    val nearbyPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val allGranted = results.values.all { it }
        hasNearbyPerms = allGranted
        permissionDeniedNote = !allGranted
        if (allGranted) {
            onStartNearbyDiscovery()
        }
    }

    LaunchedEffect(titleTab, hasNearbyPerms) {
        if (titleTab == TitleModeTab.NEARBY && hasNearbyPerms) {
            onStartNearbyDiscovery()
        } else if (titleTab != TitleModeTab.NEARBY) {
            onStopNearbyDiscovery()
        }
    }

    DisposableEffect(Unit) {
        onDispose { onStopNearbyDiscovery() }
    }

    val playerPalette = characterPalette(playerChar)
    val partnerPalette = characterPalette(partnerChar)
    val opponentPalette = characterPalette(opponentChar)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CourtApron),
        contentAlignment = Alignment.Center,
    ) {
        // Clean crisp court-line ambient background (no ghosted splash text)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            NavyDeep,
                            CourtApron,
                            NavySurface,
                        ),
                    ),
                ),
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
            val lineColor = Color.White.copy(alpha = 0.06f)
            val accentColor = LimeAccent.copy(alpha = 0.08f)
            val courtW = size.width * 0.84f
            val courtH = size.height * 0.72f
            val left = (size.width - courtW) / 2f
            val top = (size.height - courtH) / 2f
            drawRect(
                color = lineColor,
                topLeft = Offset(left, top),
                size = Size(courtW, courtH),
                style = Stroke(width = 2.dp.toPx()),
            )
            // Center net line
            val netY = top + courtH * 0.5f
            drawLine(
                color = accentColor,
                start = Offset(left - 12.dp.toPx(), netY),
                end = Offset(left + courtW + 12.dp.toPx(), netY),
                strokeWidth = 3.dp.toPx(),
            )
            // Kitchen lines
            val nvzOffset = courtH * 0.16f
            drawLine(lineColor, Offset(left, netY - nvzOffset), Offset(left + courtW, netY - nvzOffset), 1.5.dp.toPx())
            drawLine(lineColor, Offset(left, netY + nvzOffset), Offset(left + courtW, netY + nvzOffset), 1.5.dp.toPx())
            // Center service lines
            val cx = left + courtW * 0.5f
            drawLine(lineColor, Offset(cx, top), Offset(cx, netY - nvzOffset), 1.5.dp.toPx())
            drawLine(lineColor, Offset(cx, netY + nvzOffset), Offset(cx, top + courtH), 1.5.dp.toPx())
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Top bar: Stats & Ranks, Learn, Mute
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onOpenStatsHub,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("menu_stats_button"),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, BorderSlate),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = NavyCard,
                        contentColor = TextPrimary,
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.BarChart,
                        contentDescription = null,
                        tint = LimeAccent,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.stats_and_ranks),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                    )
                }

                OutlinedButton(
                    onClick = onOpenLearn,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("menu_learn_button"),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, LimeAccent.copy(alpha = 0.55f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = NavyCard,
                        contentColor = TextPrimary,
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                        tint = LimeAccent,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.learn),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                    )
                }

                OutlinedButton(
                    onClick = onToggleMute,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("menu_mute_button"),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = NavyDeep.copy(alpha = 0.86f),
                        contentColor = TextPrimary,
                    ),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Icon(
                        imageVector = if (muted) {
                            Icons.AutoMirrored.Filled.VolumeOff
                        } else {
                            Icons.AutoMirrored.Filled.VolumeUp
                        },
                        contentDescription = if (muted) "Unmute audio" else "Mute audio",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            // Centerpiece: Outlined Stacked PICKLE / RALLY Title + Character Card + Sign-in strip
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                StackedBrandTitle()

                Spacer(Modifier.height(14.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = NavyCard,
                    border = BorderStroke(1.dp, BorderSlate),
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
                            CharacterPortrait(characterId = playerChar, size = 50.dp)
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        text = if (hasCustomProfileName) profileName else "Set Profile Name",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (hasCustomProfileName) TextPrimary else LimeAccent,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .weight(1f, fill = false)
                                            .clickable {
                                                profileNameDraft = if (hasCustomProfileName) profileName else ""
                                                pendingNearbyAction = null
                                                showProfileNameDialog = true
                                            },
                                    )
                                    IconButton(
                                        onClick = {
                                            profileNameDraft = if (hasCustomProfileName) profileName else ""
                                            pendingNearbyAction = null
                                            showProfileNameDialog = true
                                        },
                                        modifier = Modifier
                                            .size(32.dp)
                                            .testTag("edit_profile_name_button"),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Edit,
                                            contentDescription = "Edit profile name",
                                            tint = LimeAccent,
                                            modifier = Modifier.size(15.dp),
                                        )
                                    }
                                }
                                Text(
                                    text = "Hero: ${playerPalette.name} · ${playerPalette.tagline}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        Spacer(Modifier.width(8.dp))

                        OutlinedButton(
                            onClick = onOpenCharacterSelect,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("roster_button"),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, BorderSlate),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = NavyElevated,
                                contentColor = TextPrimary,
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.roster),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Google Sign-In Status / Interactive Trigger
                if (authUser == null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = NavyCard,
                        border = BorderStroke(1.dp, BorderSlate),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Cloud Sync & Rankings",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextPrimary,
                                )
                                Text(
                                    text = authError ?: "Sign in with Google to save stats online",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (authError != null) RoseFault else TextSecondary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    authLoading = true
                                    authError = null
                                    GoogleAuthManager.onGoogleSignInClicked(
                                        context = context,
                                        credentialManager = credentialManager,
                                        scope = scope,
                                        onAuthSuccess = { authLoading = false },
                                        onAuthError = { msg ->
                                            authLoading = false
                                            authError = msg
                                        },
                                        onAuthCancelled = { authLoading = false },
                                    )
                                },
                                enabled = !authLoading,
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("menu_google_sign_in_button"),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NavyElevated,
                                    contentColor = LimeAccent,
                                ),
                                border = BorderStroke(1.dp, LimeAccent.copy(alpha = 0.45f)),
                                contentPadding = PaddingValues(horizontal = 12.dp),
                            ) {
                                if (authLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = LimeAccent,
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    Text(
                                        text = stringResource(R.string.sign_in_with_google),
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Mode Tabs & Mode Panel
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = NavyDeep.copy(alpha = 0.90f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        ModeTabChip(
                            label = "Solo",
                            icon = Icons.Filled.SmartToy,
                            selected = titleTab == TitleModeTab.SOLO,
                            activeTint = TextPrimary,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("mode_tab_solo"),
                            onClick = { onSelectTitleTab(TitleModeTab.SOLO) },
                        )
                        ModeTabChip(
                            label = "2v2",
                            icon = Icons.Filled.Group,
                            selected = titleTab == TitleModeTab.DOUBLES,
                            activeTint = LimeAccent,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("mode_tab_doubles"),
                            onClick = { onSelectTitleTab(TitleModeTab.DOUBLES) },
                        )
                        ModeTabChip(
                            label = "Online",
                            icon = Icons.Filled.Group,
                            selected = titleTab == TitleModeTab.ONLINE,
                            activeTint = LimeAccent,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("mode_tab_online"),
                            onClick = { onSelectTitleTab(TitleModeTab.ONLINE) },
                        )
                        ModeTabChip(
                            label = "Nearby",
                            icon = Icons.Filled.Wifi,
                            selected = titleTab == TitleModeTab.NEARBY,
                            activeTint = LimeAccent,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("mode_tab_nearby"),
                            onClick = { onSelectTitleTab(TitleModeTab.NEARBY) },
                        )
                    }
                }

                when (titleTab) {
                    TitleModeTab.SOLO, TitleModeTab.DOUBLES -> {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .testTag("match_settings_button")
                                .clickable { showSettingsSheet = true },
                            shape = RoundedCornerShape(12.dp),
                            color = NavyDeep.copy(alpha = 0.85f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Tune,
                                        contentDescription = null,
                                        tint = LimeAccent,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        text = stringResource(R.string.match_settings),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = TextPrimary,
                                    )
                                }
                                val summary = buildString {
                                    append(if (scoringMode == ScoringMode.RALLY) "Rally" else "Side-Out")
                                    append(" · ")
                                    append(if (titleTab == TitleModeTab.SOLO) pointsToWin else 11)
                                    append(" · ")
                                    append(
                                        if (titleTab == TitleModeTab.SOLO) {
                                            if (bestOf == 3) "Best of 3" else "1 Game"
                                        } else {
                                            "0-0-2"
                                        },
                                    )
                                }
                                Text(
                                    text = summary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        if (titleTab == TitleModeTab.DOUBLES) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = NavyDeep.copy(alpha = 0.80f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "Team: ${playerPalette.name} & ${partnerPalette.name}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = TextPrimary,
                                    )
                                    Text(
                                        text = "Start 0-0-2",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = LimeAccent,
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = onStartSoloOrDoubles,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 50.dp)
                                .testTag("play_match_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = LimeAccent,
                                contentColor = NavyDeep,
                            ),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (titleTab == TitleModeTab.DOUBLES) {
                                    "Play 2v2 (${playerPalette.name} & ${partnerPalette.name})"
                                } else {
                                    "Play vs ${opponentPalette.name} · ${BOT_PROFILES.getValue(difficulty).label}"
                                },
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            )
                        }
                    }

                    TitleModeTab.ONLINE -> {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = NavyDeep.copy(alpha = 0.90f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Group,
                                    contentDescription = null,
                                    tint = TextSecondary,
                                    modifier = Modifier.size(26.dp),
                                )
                                Text(
                                    text = stringResource(R.string.online_coming_soon_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TextPrimary,
                                )
                                Text(
                                    text = stringResource(R.string.online_coming_soon_body),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }

                    TitleModeTab.NEARBY -> {
                        NearbyLobbyCard(
                            profileName = if (hasCustomProfileName) profileName else playerPalette.name,
                            hasCustomProfileName = hasCustomProfileName,
                            heroName = playerPalette.name,
                            nearbyHosts = nearbyHosts,
                            nearbyError = nearbyError,
                            hasPermissions = hasNearbyPerms,
                            permissionDenied = permissionDeniedNote,
                            onEditProfileName = {
                                profileNameDraft = if (hasCustomProfileName) profileName else ""
                                pendingNearbyAction = null
                                showProfileNameDialog = true
                            },
                            onRequestPermissions = {
                                nearbyPermissionLauncher.launch(
                                    NearbyConnectionsManager.requiredPermissions(),
                                )
                            },
                            onHostNearby = {
                                if (!hasCustomProfileName) {
                                    profileNameDraft = ""
                                    pendingNearbyAction = onHostNearby
                                    showProfileNameDialog = true
                                } else {
                                    onHostNearby()
                                }
                            },
                            onJoinNearby = { host ->
                                if (!hasCustomProfileName) {
                                    profileNameDraft = ""
                                    pendingNearbyAction = { onJoinNearby(host) }
                                    showProfileNameDialog = true
                                } else {
                                    onJoinNearby(host)
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (showSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = NavySurface,
            contentColor = TextPrimary,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.match_settings),
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                    )
                    Button(
                        onClick = { showSettingsSheet = false },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("settings_done_button"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LimeAccent,
                            contentColor = NavyDeep,
                        ),
                    ) {
                        Text(
                            text = stringResource(R.string.done),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }

                // Scoring Mode
                Text(
                    text = "SCORING",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = NavyDeep,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        val ptsLabel = if (titleTab == TitleModeTab.SOLO) pointsToWin else 11
                        ScoringOptionButton(
                            label = "Rally to $ptsLabel",
                            selected = scoringMode == ScoringMode.RALLY,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("scoring_rally_button"),
                            onClick = { onSelectScoringMode(ScoringMode.RALLY) },
                        )
                        ScoringOptionButton(
                            label = "Side-Out to $ptsLabel",
                            selected = scoringMode == ScoringMode.SIDE_OUT,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("scoring_sideout_button"),
                            onClick = { onSelectScoringMode(ScoringMode.SIDE_OUT) },
                        )
                    }
                }

                // Opponent Level
                Text(
                    text = "OPPONENT LEVEL",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = NavyDeep,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        BotDifficulty.entries.forEach { level ->
                            val selected = difficulty == level
                            val bg = when {
                                !selected -> Color.Transparent
                                level == BotDifficulty.EASY -> LimeAccent.copy(alpha = 0.25f)
                                level == BotDifficulty.PRO -> OrangeAccent.copy(alpha = 0.25f)
                                else -> NavyElevated
                            }
                            val fg = when {
                                !selected -> TextSecondary
                                level == BotDifficulty.EASY -> LimeBright
                                level == BotDifficulty.PRO -> Color(0xFFFED7AA)
                                else -> TextPrimary
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(bg)
                                    .testTag("difficulty_${level.name.lowercase()}")
                                    .clickable { onSelectDifficulty(level) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = BOT_PROFILES.getValue(level).label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = fg,
                                )
                            }
                        }
                    }
                }

                // Game Length & Best Of (Solo only)
                if (titleTab == TitleModeTab.SOLO) {
                    Text(
                        text = "GAME LENGTH (WIN BY 2)",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            color = NavyDeep,
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                        ) {
                            Row(
                                modifier = Modifier.padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                listOf(11, 15, 21).forEach { pts ->
                                    val selected = pointsToWin == pts
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 40.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (selected) NavyElevated else Color.Transparent)
                                            .testTag("points_to_win_$pts")
                                            .clickable { onSelectPointsToWin(pts) },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = pts.toString(),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (selected) TextPrimary else TextSecondary,
                                        )
                                    }
                                }
                            }
                        }

                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            color = NavyDeep,
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                        ) {
                            Row(
                                modifier = Modifier.padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                listOf(1, 3).forEach { series ->
                                    val selected = bestOf == series
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 40.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (selected) NavyElevated else Color.Transparent)
                                            .testTag("best_of_$series")
                                            .clickable { onSelectBestOf(series) },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = if (series == 1) "1 Game" else "Best of 3",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (selected) TextPrimary else TextSecondary,
                                            maxLines = 1,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }

    if (showProfileNameDialog) {
        val trimmedDraft = profileNameDraft.trim().take(24)
        AlertDialog(
            onDismissRequest = {
                showProfileNameDialog = false
                pendingNearbyAction = null
            },
            containerColor = NavySurface,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = {
                Text(
                    text = "Set Profile Name",
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Your profile name shows on the court during matches, while your hero name (${playerPalette.name}) stays the same.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                    OutlinedTextField(
                        value = profileNameDraft,
                        onValueChange = { if (it.length <= 24) profileNameDraft = it },
                        singleLine = true,
                        label = { Text("Profile Name") },
                        placeholder = { Text("e.g. Alex") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile_name_dialog_input"),
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
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (trimmedDraft.isNotEmpty()) {
                            onSaveProfileName(trimmedDraft)
                        }
                        val nextAction = pendingNearbyAction
                        showProfileNameDialog = false
                        pendingNearbyAction = null
                        nextAction?.invoke()
                    },
                    enabled = trimmedDraft.isNotEmpty(),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("profile_name_dialog_save"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LimeAccent,
                        contentColor = NavyDeep,
                    ),
                ) {
                    Text(if (pendingNearbyAction != null) "Save & Play" else "Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        val nextAction = pendingNearbyAction
                        showProfileNameDialog = false
                        pendingNearbyAction = null
                        nextAction?.invoke()
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(
                        text = if (pendingNearbyAction != null) "Skip" else "Cancel",
                        color = TextSecondary,
                    )
                }
            },
        )
    }
}

@Composable
private fun StackedBrandTitle() {
    val context = LocalContext.current
    val typeface = remember(context) {
        ResourcesCompat.getFont(context, R.font.bricolage_grotesque) ?: Typeface.DEFAULT_BOLD
    }
    Canvas(
        modifier = Modifier
            .width(268.dp)
            .height(98.dp),
    ) {
        val nc = drawContext.canvas.nativeCanvas
        val scale = size.width / 280f
        fun drawWord(word: String, y: Float, fillColor: Color) {
            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = NavyDeep.toArgb()
                this.textAlign = Paint.Align.CENTER
                this.textSize = 40f * scale
                this.typeface = Typeface.create(typeface, Typeface.BOLD)
                this.letterSpacing = 0.08f
                this.style = Paint.Style.STROKE
                this.strokeWidth = 8f * scale
                this.strokeJoin = Paint.Join.ROUND
            }
            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = fillColor.toArgb()
                this.textAlign = Paint.Align.CENTER
                this.textSize = 40f * scale
                this.typeface = Typeface.create(typeface, Typeface.BOLD)
                this.letterSpacing = 0.08f
            }
            nc.drawText(word, size.width / 2f, y * scale, strokePaint)
            nc.drawText(word, size.width / 2f, y * scale, fillPaint)
        }
        drawWord("PICKLE", 42f, LimeBright)
        drawWord("RALLY", 84f, OrangeAccent)
    }
}

@Composable
private fun ModeTabChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    activeTint: Color,
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
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) activeTint else TextSecondary,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = if (selected) activeTint else TextSecondary,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ScoringOptionButton(
    label: String,
    selected: Boolean,
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
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) TextPrimary else TextSecondary,
            maxLines = 1,
        )
    }
}

@Composable
private fun NearbyLobbyCard(
    profileName: String,
    hasCustomProfileName: Boolean,
    heroName: String,
    nearbyHosts: List<NearbyHostInfo>,
    nearbyError: String?,
    hasPermissions: Boolean,
    permissionDenied: Boolean,
    onEditProfileName: () -> Unit,
    onRequestPermissions: () -> Unit,
    onHostNearby: () -> Unit,
    onJoinNearby: (NearbyHostInfo) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = NavyDeep.copy(alpha = 0.90f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Play 1v1 with someone next to you over Wi-Fi, hotspot or Bluetooth. No account or internet needed.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onEditProfileName)
                    .testTag("nearby_edit_name_button"),
                shape = RoundedCornerShape(10.dp),
                color = NavyCard,
                border = BorderStroke(
                    1.dp,
                    if (hasCustomProfileName) BorderSlate else LimeAccent.copy(alpha = 0.55f),
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (hasCustomProfileName) "Name: $profileName" else "Set Your Profile Name",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (hasCustomProfileName) TextPrimary else LimeAccent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "Hero: $heroName",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Edit profile name",
                            tint = LimeAccent,
                            modifier = Modifier.size(15.dp),
                        )
                        Text(
                            text = if (hasCustomProfileName) "Edit" else "Set",
                            style = MaterialTheme.typography.labelMedium,
                            color = LimeAccent,
                        )
                    }
                }
            }

            if (!hasPermissions) {
                Button(
                    onClick = onRequestPermissions,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("grant_nearby_permissions_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LimeAccent,
                        contentColor = NavyDeep,
                    ),
                ) {
                    Icon(Icons.Filled.Wifi, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (permissionDenied) "Allow Nearby Permissions" else "Enable Nearby Search",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            } else {
                Button(
                    onClick = onHostNearby,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("host_nearby_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LimeAccent,
                        contentColor = NavyDeep,
                    ),
                ) {
                    Icon(Icons.Filled.Wifi, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Host Nearby Game", style = MaterialTheme.typography.labelLarge)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Players nearby",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextMuted,
                    )
                    Text(
                        text = "Searching…",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                    )
                }

                if (nearbyHosts.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.Transparent,
                        border = BorderStroke(1.dp, BorderSlate),
                    ) {
                        Text(
                            text = "No games found yet. Ask your friend to tap \"Host Nearby Game\".",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 14.dp),
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        nearbyHosts.forEach { host ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onJoinNearby(host) },
                                shape = RoundedCornerShape(10.dp),
                                color = com.picklerally.app.ui.theme.NavyCard,
                                border = BorderStroke(1.dp, BorderSlate),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    CharacterPortrait(characterId = host.characterId, size = 30.dp)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${host.hostName}'s court",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = TextPrimary,
                                        )
                                        Text(
                                            text = "${host.roomCode} · Hero: ${characterPalette(host.characterId).name}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextSecondary,
                                        )
                                    }
                                    Text(
                                        text = "Join",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = LimeAccent,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (nearbyError != null) {
                Text(
                    text = nearbyError,
                    style = MaterialTheme.typography.bodySmall,
                    color = RoseFault,
                )
            }
        }
    }
}
