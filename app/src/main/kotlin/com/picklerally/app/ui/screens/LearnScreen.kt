package com.picklerally.app.ui.screens

import android.graphics.Paint
import android.graphics.Typeface
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.picklerally.app.R
import com.picklerally.app.ui.components.CourtDiagram
import com.picklerally.app.ui.theme.AmberYellow
import com.picklerally.app.ui.theme.BorderSlate
import com.picklerally.app.ui.theme.CourtApron
import com.picklerally.app.ui.theme.LimeAccent
import com.picklerally.app.ui.theme.NavyDeep
import com.picklerally.app.ui.theme.NavyElevated
import com.picklerally.app.ui.theme.OrangeSoft
import com.picklerally.app.ui.theme.RoseFault
import com.picklerally.app.ui.theme.TextMuted
import com.picklerally.app.ui.theme.TextPrimary
import com.picklerally.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.sin

private enum class LearnTab(val label: String) {
    WATCH("Watch"),
    RULES("Rules"),
    CONTROLS("Controls"),
    TIPS("Tips"),
}

private data class LearnItem(
    val title: String,
    val points: List<String>,
)

private val RULES_ITEMS = listOf(
    LearnItem(
        "1. The court",
        listOf(
            "Court is 20 ft wide and 44 ft long, the same for singles and doubles.",
            "The net is 36 in high at the sidelines and 34 in at the center.",
            "The 7 ft zone on each side of the net is the Non-Volley Zone, called the \"kitchen\".",
            "Behind the kitchen, each half is split into a right and a left service box.",
        ),
    ),
    LearnItem(
        "2. Serving",
        listOf(
            "Serve underhand: hit the ball below your waist with an upward arc (or use a drop serve).",
            "Keep both feet behind the baseline until you hit the ball.",
            "Serve diagonally into the opposite service box. It must clear the kitchen.",
            "A serve that lands on the kitchen line is a fault. Landing on any other line of the box is in.",
            "A serve that clips the net and lands in the correct box is in play (no re-serve).",
            "You get one serve attempt per point.",
        ),
    ),
    LearnItem(
        "3. Two-bounce rule",
        listOf(
            "The return of serve must bounce before it is hit: the receiver cannot volley the serve.",
            "The serving side must also let the return bounce before hitting the 3rd shot.",
            "After those two bounces, either side may volley (hit it in the air) or play it off the bounce.",
        ),
    ),
    LearnItem(
        "4. The kitchen (Non-Volley Zone)",
        listOf(
            "You may not volley while standing in the kitchen or touching the kitchen line.",
            "If your momentum carries you into the kitchen after a volley, it is still a fault.",
            "You may step into the kitchen at any time to hit a ball that has bounced.",
        ),
    ),
    LearnItem(
        "5. Faults (you lose the rally)",
        listOf(
            "Hitting the ball out of bounds or into the net.",
            "Volleying before the two-bounce rule allows it.",
            "Volleying from the kitchen.",
            "Letting the ball bounce twice on your side.",
            "Balls touching a line are in (except the kitchen line on a serve).",
        ),
    ),
    LearnItem(
        "6. Scoring (traditional / side-out)",
        listOf(
            "Only the serving side can score a point.",
            "If the server loses the rally, the serve passes to the opponent (\"side out\").",
            "Games are usually played to 11 points and must be won by 2 (some to 15 or 21).",
            "Tournament matches are often best of 3 games.",
        ),
    ),
    LearnItem(
        "7. Singles serving & score call",
        listOf(
            "Serve from the right box when your score is even (0, 2, 4...).",
            "Serve from the left box when your score is odd (1, 3, 5...).",
            "Call the score before serving: server score first, then receiver score, e.g. \"4 - 2\".",
        ),
    ),
    LearnItem(
        "8. Doubles serving",
        listOf(
            "Call three numbers: serving team score, receiving team score, server number, e.g. \"4 - 2 - 1\".",
            "Both partners serve before a side out, except at the start of the game (\"0 - 0 - 2\").",
            "When the serving team wins a point, the server switches sides with their partner.",
        ),
    ),
    LearnItem(
        "9. Rally scoring (variant)",
        listOf(
            "A point is scored on every rally, no matter who served.",
            "The winner of the rally serves next. Games still need a 2-point lead.",
        ),
    ),
)

private val CONTROLS_ITEMS = listOf(
    LearnItem(
        "Move",
        listOf(
            "Tap or drag anywhere on your half of the court to run there.",
            "You have to move to the ball yourself: watch where the opponent aims and get there early.",
        ),
    ),
    LearnItem(
        "Hit",
        listOf(
            "The ring around your character is your reach. When the ball is inside it and a hit is legal, you swing automatically.",
            "An orange ring means the ball is close but you can't hit yet: \"Let it bounce\" (two-bounce rule), \"Step out of the kitchen\" or \"Too high\".",
            "Swipe on the court to choose and aim your own shot:",
            "Short, slow swipe = Dink (soft shot into the kitchen).",
            "Medium swipe straight up = Lob (high and deep).",
            "Long, fast swipe = Drive (low and hard). Swipe left or right to aim.",
        ),
    ),
    LearnItem(
        "Serve",
        listOf(
            "Tap \"Serve Diagonal\" or swipe up on the court when it is your serve.",
            "When the opponent serves, wait for the countdown (or \"Tap to receive\") and let their serve bounce before you hit.",
        ),
    ),
    LearnItem(
        "Match options",
        listOf(
            "Opponent level: Easy (slow, aims near you, misses more), Normal, Hard (faster, aims wider), Pro (fastest, rarely misses).",
            "2v2: your AI partner covers the other half of the court. Balls on your half are yours to play.",
            "Rally or Side-Out scoring.",
            "Games to 11, 15 or 21 (win by 2), as a single game or best of 3.",
            "The badge next to the score shows the game number and games won, e.g. \"G2 · 1-0\".",
        ),
    ),
)

private val TIPS_ITEMS = listOf(
    LearnItem(
        "Serve and return deep",
        listOf(
            "A deep serve keeps the opponent at their baseline.",
            "A deep return gives you time to move up to the kitchen line.",
        ),
    ),
    LearnItem(
        "Get to the kitchen line",
        listOf(
            "Most points are won at the net. After the two bounces, move up to just behind the kitchen line.",
            "Stay behind the line when you volley.",
        ),
    ),
    LearnItem(
        "The third-shot drop",
        listOf(
            "As the server, your 3rd shot is often a soft dink that lands in the opponent's kitchen.",
            "It gives you time to move forward while they have to hit up.",
        ),
    ),
    LearnItem(
        "Dink with patience",
        listOf(
            "When both players are at the kitchen, trade soft dinks and wait for a high ball.",
            "Attack a ball that pops up with a drive at the opponent's feet.",
        ),
    ),
    LearnItem(
        "Watch for lobs",
        listOf(
            "If you crowd the net, opponents may lob over you. Back up early when you see a high ball.",
        ),
    ),
)

private data class TutorialSceneMeta(
    val title: String,
    val caption: String,
)

private val TUTORIAL_SCENES = listOf(
    TutorialSceneMeta(
        "1. Serve diagonally",
        "Serve underhand from behind the baseline into the box diagonally across. It must fly past the kitchen. Tap \"Serve Diagonal\" or swipe up.",
    ),
    TutorialSceneMeta(
        "2. The two-bounce rule",
        "The serve must bounce, and the return must bounce too. After those two bounces, anyone may volley (hit it in the air).",
    ),
    TutorialSceneMeta(
        "3. The kitchen",
        "No volleys while standing in the kitchen (the zone next to the net). Stay behind the line to volley, or let the ball bounce first.",
    ),
    TutorialSceneMeta(
        "4. Move and hit",
        "Tap the court to run there. When the ball is inside your ring it turns green and you swing automatically. Orange means wait: \"Let it bounce\".",
    ),
    TutorialSceneMeta(
        "5. Scoring",
        "Games go to 11 (or 15/21) and you must win by 2. Rally scoring: every rally scores a point. Side-out: only the serving side can score.",
    ),
)

private const val SCENE_DURATION_SEC = 6f

@Composable
fun LearnScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    var tab by remember { mutableStateOf(LearnTab.WATCH) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CourtApron)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp),
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NavyDeep)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("learn_back_button"),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, BorderSlate),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.back), style = MaterialTheme.typography.labelLarge)
                }
                Text(
                    text = stringResource(R.string.learn_pickleball),
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                )
            }
            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

            // Tab Strip
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                shape = RoundedCornerShape(12.dp),
                color = NavyDeep.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    LearnTab.entries.forEach { entry ->
                        val active = tab == entry
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (active) NavyElevated else Color.Transparent)
                                .testTag("learn_tab_${entry.name.lowercase()}")
                                .clickable { tab = entry },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = entry.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (active) TextPrimary else TextSecondary,
                            )
                        }
                    }
                }
            }

            // Content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                when (tab) {
                    LearnTab.WATCH -> TutorialPlayer()
                    LearnTab.RULES -> {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CourtDiagram()
                        }
                        Spacer(Modifier.height(12.dp))
                        AccordionItemList(RULES_ITEMS)
                    }
                    LearnTab.CONTROLS -> AccordionItemList(CONTROLS_ITEMS)
                    LearnTab.TIPS -> AccordionItemList(TIPS_ITEMS)
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    text = stringResource(R.string.rules_attribution),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun AccordionItemList(items: List<LearnItem>) {
    var openIndex by remember { mutableIntStateOf(0) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEachIndexed { idx, item ->
            val isOpen = openIndex == idx
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = NavyDeep.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable { openIndex = if (isOpen) -1 else idx }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.labelLarge,
                            color = TextPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = if (isOpen) "–" else "+",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextSecondary,
                        )
                    }
                    AnimatedVisibility(visible = isOpen) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            item.points.forEach { pt ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .padding(top = 6.dp)
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(LimeAccent),
                                    )
                                    Text(
                                        text = pt,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextMuted,
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

@Composable
private fun TutorialPlayer() {
    var scene by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(true) }
    var elapsed by remember { mutableFloatStateOf(0f) }
    val last = TUTORIAL_SCENES.lastIndex

    fun goTo(target: Int) {
        scene = target.coerceIn(0, last)
        elapsed = 0f
        playing = true
    }

    LaunchedEffect(playing, scene) {
        if (!playing) return@LaunchedEffect
        while (playing) {
            delay(40L)
            elapsed += 0.04f
            if (elapsed >= SCENE_DURATION_SEC) {
                if (scene < last) {
                    scene += 1
                    elapsed = 0f
                } else {
                    elapsed = SCENE_DURATION_SEC
                    playing = false
                }
            }
        }
    }

    val current = TUTORIAL_SCENES[scene]
    val progress = (elapsed / SCENE_DURATION_SEC).coerceIn(0f, 1f)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = NavyDeep,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = current.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = TextPrimary,
                    )
                    Text(
                        text = "${scene + 1}/${TUTORIAL_SCENES.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary,
                    )
                }

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(
                        modifier = Modifier
                            .width(150.dp)
                            .height(250.dp),
                    ) {
                        val s = minOf(size.width / 120f, size.height / 220f)
                        val ox = (size.width - 120f * s) / 2f
                        val oy = (size.height - 220f * s) / 2f
                        drawTutorialScene(scene = scene, t = progress, ox = ox, oy = oy, s = s)
                    }
                }

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = LimeAccent,
                    trackColor = NavyElevated,
                )
            }
        }

        Text(
            text = current.caption,
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
            modifier = Modifier.heightIn(min = 48.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = { goTo(scene - 1) },
                enabled = scene > 0,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("tutorial_prev_button"),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
            ) {
                Text("◀ Prev", style = MaterialTheme.typography.labelMedium)
            }

            Button(
                onClick = {
                    if (!playing && scene == last && elapsed >= SCENE_DURATION_SEC) {
                        goTo(0)
                    } else {
                        playing = !playing
                    }
                },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("tutorial_play_pause_button"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LimeAccent,
                    contentColor = NavyDeep,
                ),
            ) {
                Text(
                    text = when {
                        playing -> "Pause"
                        scene == last && elapsed >= SCENE_DURATION_SEC -> "Replay"
                        else -> "Play"
                    },
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            OutlinedButton(
                onClick = { goTo(scene + 1) },
                enabled = scene < last,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("tutorial_next_button"),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
            ) {
                Text("Next ▶", style = MaterialTheme.typography.labelMedium)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TUTORIAL_SCENES.forEachIndexed { idx, _ ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (idx == scene) 10.dp else 8.dp)
                        .clip(CircleShape)
                        .background(if (idx == scene) LimeAccent else Color(0xFF475569))
                        .clickable { goTo(idx) },
                )
            }
        }
    }
}

private fun DrawScope.drawTutorialScene(
    scene: Int,
    t: Float,
    ox: Float,
    oy: Float,
    s: Float,
) {
    fun px(x: Float) = ox + x * s
    fun py(y: Float) = oy + y * s

    fun quadBezier(
        p0x: Float,
        p0y: Float,
        cx: Float,
        cy: Float,
        p1x: Float,
        p1y: Float,
        frac: Float,
    ): Offset {
        val u = frac.coerceIn(0f, 1f)
        val inv = 1f - u
        val x = inv * inv * p0x + 2f * inv * u * cx + u * u * p1x
        val y = inv * inv * p0y + 2f * inv * u * cy + u * u * p1y
        return Offset(px(x), py(y))
    }

    fun lerpPt(x0: Float, y0: Float, x1: Float, y1: Float, frac: Float): Offset {
        val u = frac.coerceIn(0f, 1f)
        return Offset(px(x0 + (x1 - x0) * u), py(y0 + (y1 - y0) * u))
    }

    fun label(x: Float, y: Float, text: String, color: Color = TextPrimary, size: Float = 7f) {
        val nc = drawContext.canvas.nativeCanvas
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = NavyDeep.toArgb()
            textAlign = Paint.Align.CENTER
            textSize = size * s
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            style = Paint.Style.STROKE
            strokeWidth = 2f * s
        }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color.toArgb()
            textAlign = Paint.Align.CENTER
            textSize = size * s
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        nc.drawText(text, px(x), py(y), strokePaint)
        nc.drawText(text, px(x), py(y), fillPaint)
    }

    fun playerDot(x: Float, y: Float, color: Color = Color(0xFFF59E0B), name: String? = null) {
        drawCircle(color = color, radius = 5f * s, center = Offset(px(x), py(y)))
        drawCircle(
            color = Color(0xFF0F172A),
            radius = 5f * s,
            center = Offset(px(x), py(y)),
            style = Stroke(width = 1f * s),
        )
        if (name != null) label(x, y + 12f, name, size = 6f)
    }

    // Base court
    drawRoundRect(
        color = Color(0xFF1D4ED8),
        topLeft = Offset(px(10f), py(10f)),
        size = Size(100f * s, 200f * s),
        cornerRadius = CornerRadius(2f * s, 2f * s),
    )
    drawRect(
        color = Color(0xFF1E40AF),
        topLeft = Offset(px(10f), py(75f)),
        size = Size(100f * s, 70f * s),
    )
    drawRoundRect(
        color = Color(0xFFF8FAFC),
        topLeft = Offset(px(10f), py(10f)),
        size = Size(100f * s, 200f * s),
        cornerRadius = CornerRadius(2f * s, 2f * s),
        style = Stroke(width = 1.5f * s),
    )
    drawLine(Color(0xFFF8FAFC), Offset(px(10f), py(75f)), Offset(px(110f), py(75f)), 1f * s)
    drawLine(Color(0xFFF8FAFC), Offset(px(10f), py(145f)), Offset(px(110f), py(145f)), 1f * s)
    drawLine(Color(0xFFF8FAFC), Offset(px(60f), py(10f)), Offset(px(60f), py(75f)), 1f * s)
    drawLine(Color(0xFFF8FAFC), Offset(px(60f), py(145f)), Offset(px(60f), py(210f)), 1f * s)
    drawLine(Color(0xFFF8FAFC), Offset(px(4f), py(110f)), Offset(px(116f), py(110f)), 2.5f * s)

    when (scene) {
        0 -> {
            val boxAlpha = 0.08f + 0.24f * (0.5f + 0.5f * sin(t * 20f))
            drawRect(
                color = LimeAccent.copy(alpha = boxAlpha),
                topLeft = Offset(px(10f), py(10f)),
                size = Size(50f * s, 65f * s),
            )
            playerDot(88f, 204f, name = "You")
            playerDot(35f, 22f, color = Color(0xFFA855F7), name = "Lola")

            val guide = Path().apply {
                moveTo(px(86f), py(200f))
                quadraticTo(px(55f), py(95f), px(36f), py(44f))
            }
            drawPath(
                path = guide,
                color = Color(0xFFFDE68A).copy(alpha = 0.5f),
                style = Stroke(
                    width = 1.2f * s,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f * s, 2f * s)),
                ),
            )

            val ballPos = quadBezier(86f, 200f, 55f, 95f, 36f, 44f, (t - 0.15f) / 0.30f)
            drawCircle(color = AmberYellow, radius = 2.6f * s, center = ballPos)

            if (t >= 0.46f) {
                drawCircle(
                    color = LimeAccent,
                    radius = 6f * s,
                    center = Offset(px(36f), py(44f)),
                    style = Stroke(width = 1.5f * s),
                )
                label(60f, 64f, "In the box!", LimeAccent)
            }
        }
        1 -> {
            playerDot(88f, 204f, name = "You")
            playerDot(35f, 22f, color = Color(0xFFA855F7), name = "Lola")

            if (t in 0.02f..0.30f) {
                drawCircle(
                    color = AmberYellow,
                    radius = 2.6f * s,
                    center = quadBezier(86f, 200f, 55f, 95f, 36f, 44f, (t - 0.05f) / 0.23f),
                )
            }
            if (t >= 0.28f) {
                drawCircle(
                    color = LimeAccent,
                    radius = 5f * s,
                    center = Offset(px(36f), py(44f)),
                    style = Stroke(width = 1.5f * s),
                )
                label(36f, 58f, "Bounce 1", LimeAccent, size = 6f)
            }
            if (t in 0.33f..0.62f) {
                drawCircle(
                    color = AmberYellow,
                    radius = 2.6f * s,
                    center = quadBezier(36f, 46f, 62f, 120f, 84f, 178f, (t - 0.34f) / 0.26f),
                )
            }
            if (t >= 0.60f) {
                drawCircle(
                    color = LimeAccent,
                    radius = 5f * s,
                    center = Offset(px(84f), py(178f)),
                    style = Stroke(width = 1.5f * s),
                )
                label(84f, 170f, "Bounce 2", LimeAccent, size = 6f)
            }
            if (t >= 0.68f) {
                drawCircle(
                    color = AmberYellow,
                    radius = 2.6f * s,
                    center = quadBezier(84f, 184f, 70f, 100f, 70f, 52f, (t - 0.69f) / 0.21f),
                )
            }
            if (t >= 0.72f) {
                label(60f, 100f, "Now volleys are OK", Color(0xFFFDE68A))
            }
        }
        2 -> {
            val kAlpha = 0.08f + 0.22f * (0.5f + 0.5f * sin(t * 16f))
            drawRect(
                color = OrangeSoft.copy(alpha = kAlpha),
                topLeft = Offset(px(10f), py(110f)),
                size = Size(100f * s, 35f * s),
            )
            label(60f, 140f, "KITCHEN", Color(0xFFFDE68A), size = 6f)
            playerDot(35f, 124f, name = "In kitchen")
            playerDot(85f, 160f, name = "Behind line")

            if (t in 0.05f..0.32f) {
                drawCircle(
                    color = AmberYellow,
                    radius = 2.6f * s,
                    center = quadBezier(38f, 30f, 42f, 80f, 36f, 119f, (t - 0.06f) / 0.24f),
                )
            }
            if (t >= 0.30f) {
                label(35f, 100f, "Fault!", RoseFault, size = 8f)
            }
            if (t in 0.45f..0.72f) {
                drawCircle(
                    color = AmberYellow,
                    radius = 2.6f * s,
                    center = quadBezier(82f, 30f, 86f, 100f, 85f, 155f, (t - 0.46f) / 0.24f),
                )
            }
            if (t >= 0.70f) {
                label(85f, 100f, "Volley OK", LimeAccent, size = 8f)
            }
        }
        3 -> {
            if (t in 0.06f..0.22f) {
                val r = 2f + 8f * ((t * 6f) % 1f)
                drawCircle(
                    color = TextPrimary,
                    radius = r * s,
                    center = Offset(px(40f), py(168f)),
                    style = Stroke(width = 1f * s),
                )
                label(40f, 186f, "Tap here", size = 6f)
            }
            val playerPos = lerpPt(85f, 200f, 40f, 168f, (t - 0.12f) / 0.22f)
            drawCircle(
                color = Color(0xFF94A3B8),
                radius = 13f * s,
                center = playerPos,
                style = Stroke(
                    width = 1f * s,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f * s, 2f * s)),
                ),
            )
            if (t in 0.46f..0.60f) {
                drawCircle(
                    color = OrangeSoft,
                    radius = 13f * s,
                    center = playerPos,
                    style = Stroke(width = 2f * s),
                )
            }
            if (t in 0.60f..0.85f) {
                drawCircle(
                    color = LimeAccent.copy(alpha = 0.2f),
                    radius = 13f * s,
                    center = playerPos,
                )
                drawCircle(
                    color = LimeAccent,
                    radius = 13f * s,
                    center = playerPos,
                    style = Stroke(width = 2f * s),
                )
            }
            drawCircle(color = Color(0xFFF59E0B), radius = 5f * s, center = playerPos)
            drawCircle(
                color = Color(0xFF0F172A),
                radius = 5f * s,
                center = playerPos,
                style = Stroke(width = 1f * s),
            )

            if (t in 0.36f..0.60f) {
                drawCircle(
                    color = AmberYellow,
                    radius = 2.6f * s,
                    center = quadBezier(70f, 30f, 52f, 110f, 44f, 150f, (t - 0.37f) / 0.19f),
                )
            }
            if (t in 0.47f..0.60f) {
                label(40f, 140f, "Let it bounce", OrangeSoft, size = 6f)
            }
            if (t >= 0.60f) {
                drawCircle(
                    color = AmberYellow,
                    radius = 2.6f * s,
                    center = quadBezier(44f, 156f, 62f, 90f, 76f, 36f, (t - 0.64f) / 0.24f),
                )
            }
            if (t in 0.60f..0.90f) {
                label(40f, 140f, "Swing!", LimeAccent, size = 7f)
            }
        }
        4 -> {
            drawRoundRect(
                color = NavyDeep,
                topLeft = Offset(px(16f), py(70f)),
                size = Size(88f * s, 80f * s),
                cornerRadius = CornerRadius(8f * s, 8f * s),
            )
            drawRoundRect(
                color = BorderSlate,
                topLeft = Offset(px(16f), py(70f)),
                size = Size(88f * s, 80f * s),
                cornerRadius = CornerRadius(8f * s, 8f * s),
                style = Stroke(width = 1f * s),
            )
            label(60f, 86f, "You  –  Lola", Color(0xFF94A3B8), size = 7f)
            when {
                t < 0.22f -> label(60f, 118f, "0 – 0", size = 20f)
                t < 0.46f -> label(60f, 118f, "6 – 4", size = 20f)
                t < 0.70f -> {
                    label(60f, 118f, "10 – 10", size = 20f)
                    label(60f, 138f, "Need a 2-point lead", OrangeSoft, size = 6f)
                }
                else -> {
                    label(60f, 118f, "12 – 10", LimeAccent, size = 20f)
                    label(60f, 138f, "Game! Won by 2", LimeAccent, size = 7f)
                }
            }
        }
    }
}
