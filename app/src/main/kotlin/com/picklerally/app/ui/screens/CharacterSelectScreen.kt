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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.picklerally.app.R
import com.picklerally.app.game.CharacterId
import com.picklerally.app.game.ROSTER_ORDER
import com.picklerally.app.game.characterPalette
import com.picklerally.app.ui.components.CharacterPortrait
import com.picklerally.app.ui.theme.AmberGold
import com.picklerally.app.ui.theme.BorderSlate
import com.picklerally.app.ui.theme.LimeAccent
import com.picklerally.app.ui.theme.NavyCard
import com.picklerally.app.ui.theme.NavyDeep
import com.picklerally.app.ui.theme.NavySelected
import com.picklerally.app.ui.theme.NavySurface
import com.picklerally.app.ui.theme.OrangeAccent
import com.picklerally.app.ui.theme.SkyAccent
import com.picklerally.app.ui.theme.TextMuted
import com.picklerally.app.ui.theme.TextPrimary
import com.picklerally.app.ui.theme.TextSecondary

@Composable
fun CharacterSelectScreen(
    playerChar: CharacterId,
    partnerChar: CharacterId,
    opponentChar: CharacterId,
    dillUnlocked: Boolean,
    onSelectPlayerChar: (CharacterId) -> Unit,
    onSelectPartnerChar: (CharacterId) -> Unit,
    onSelectOpponentChar: (CharacterId) -> Unit,
    onToggleDillUnlock: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    val playerPalette = characterPalette(playerChar)

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
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("character_select_back_button"),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, BorderSlate),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = NavyCard,
                        contentColor = TextPrimary,
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.back),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = stringResource(R.string.select_fighter),
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                    )
                    Text(
                        text = stringResource(R.string.cosmetic_equal_stats),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                    )
                }
            }

            HorizontalDivider(color = Color(0xFF1E293B))

            // Scrollable roster grid + unlock button
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val rows = ROSTER_ORDER.chunked(3)
                rows.forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rowItems.forEach { id ->
                            val pal = characterPalette(id)
                            val locked = pal.isUnlockable && !dillUnlocked
                            val selected = playerChar == id
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .testTag("character_card_${id.name.lowercase()}")
                                    .clickable { onSelectPlayerChar(id) },
                                shape = RoundedCornerShape(12.dp),
                                color = if (selected) NavySelected else NavyCard,
                                border = BorderStroke(
                                    width = if (selected) 1.5.dp else 1.dp,
                                    color = if (selected) LimeAccent else Color(0xFF1E293B),
                                ),
                            ) {
                                Column(
                                    modifier = Modifier.padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    CharacterPortrait(
                                        characterId = id,
                                        size = 44.dp,
                                        locked = locked,
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Text(
                                            text = pal.name,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = TextPrimary,
                                        )
                                        if (locked) {
                                            Icon(
                                                imageVector = Icons.Filled.Lock,
                                                contentDescription = "Locked",
                                                tint = TextSecondary,
                                                modifier = Modifier.size(12.dp),
                                            )
                                        }
                                    }
                                    Text(
                                        text = pal.tagline,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = LimeAccent,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = pal.description,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                        color = TextSecondary,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 12.sp,
                                    )
                                }
                            }
                        }
                        repeat(3 - rowItems.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }

                OutlinedButton(
                    onClick = onToggleDillUnlock,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("toggle_dill_unlock_button"),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, BorderSlate),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = NavyCard,
                        contentColor = TextPrimary,
                    ),
                ) {
                    Icon(
                        imageVector = if (dillUnlocked) Icons.Filled.LockOpen else Icons.Filled.Lock,
                        contentDescription = null,
                        tint = if (dillUnlocked) LimeAccent else AmberGold,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (dillUnlocked) {
                            "Dill Mascot Unlocked (Tap to Lock)"
                        } else {
                            "Unlock Dill Mascot"
                        },
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }

            // Bottom partner / opponent / confirm controls
            HorizontalDivider(color = Color(0xFF1E293B))
            Spacer(Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RoleSelectorStrip(
                    title = stringResource(R.string.doubles_partner_label),
                    selected = partnerChar,
                    activeColor = SkyAccent,
                    onSelect = onSelectPartnerChar,
                    tagPrefix = "partner",
                )
                RoleSelectorStrip(
                    title = stringResource(R.string.primary_opponent_label),
                    selected = opponentChar,
                    activeColor = OrangeAccent,
                    onSelect = onSelectOpponentChar,
                    tagPrefix = "opponent",
                )
                Button(
                    onClick = onBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("confirm_character_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LimeAccent,
                        contentColor = NavyDeep,
                    ),
                ) {
                    Text(
                        text = "Confirm ${playerPalette.name}",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    )
                }
            }
        }
    }
}

@Composable
private fun RoleSelectorStrip(
    title: String,
    selected: CharacterId,
    activeColor: Color,
    onSelect: (CharacterId) -> Unit,
    tagPrefix: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
        )
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = NavyCard,
            border = BorderStroke(1.dp, Color(0xFF1E293B)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ROSTER_ORDER.chunked(5).forEach { chunk ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        chunk.forEach { id ->
                            val isActive = selected == id
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 34.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(if (isActive) activeColor else Color.Transparent)
                                    .testTag("${tagPrefix}_${id.name.lowercase()}")
                                    .clickable { onSelect(id) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = characterPalette(id).name,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                    color = if (isActive) NavyDeep else TextMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
