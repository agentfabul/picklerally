package com.picklerally.app.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.picklerally.app.game.BallPhysicsConstants
import com.picklerally.app.game.BallState
import com.picklerally.app.game.CharacterId
import com.picklerally.app.game.CourtSide
import com.picklerally.app.game.CourtSpec
import com.picklerally.app.game.FaultReason
import com.picklerally.app.game.PlayerEntity
import com.picklerally.app.game.PlayerHitState
import com.picklerally.app.game.RallyPhase
import com.picklerally.app.game.ServeAssignment
import com.picklerally.app.game.ServiceBoxSide
import com.picklerally.app.game.SinglePlayerMatch
import com.picklerally.app.game.characterPalette
import com.picklerally.app.game.spriteScaleForHeight
import com.picklerally.app.ui.theme.CourtApron
import com.picklerally.app.ui.theme.CourtBlue
import com.picklerally.app.ui.theme.GoldBall
import com.picklerally.app.ui.theme.KitchenBlue
import com.picklerally.app.ui.theme.LimeAccent
import com.picklerally.app.ui.theme.LimeBright
import com.picklerally.app.ui.theme.LineWhite
import com.picklerally.app.ui.theme.NavyDeep
import com.picklerally.app.ui.theme.OrangeSoft
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

data class CourtViewportMetrics(
    val width: Float,
    val height: Float,
    val drawW: Float,
    val drawH: Float,
    val offsetX: Float,
    val offsetY: Float,
    val scale: Float,
) {
    fun worldToScreen(wx: Float, wy: Float): Offset = Offset(
        x = offsetX + wx * scale,
        y = offsetY + (CourtSpec.WORLD_HEIGHT_FT - wy) * scale,
    )

    fun screenToWorld(sx: Float, sy: Float): Pair<Float, Float> {
        val safeScale = max(1e-3f, scale)
        val wx = ((sx - offsetX) / safeScale).coerceIn(0f, CourtSpec.WORLD_WIDTH_FT)
        val wy = (CourtSpec.WORLD_HEIGHT_FT - (sy - offsetY) / safeScale)
            .coerceIn(0f, CourtSpec.WORLD_HEIGHT_FT)
        return wx to wy
    }

    companion object {
        const val VIEW_MIN_X = 2.6f
        const val VIEW_MAX_X = 25.4f
        const val VIEW_WIDTH_FT = VIEW_MAX_X - VIEW_MIN_X
        const val VIEW_MIN_Y = 2.4f
        const val VIEW_MAX_Y = 53.2f
        const val VIEW_HEIGHT_FT = VIEW_MAX_Y - VIEW_MIN_Y

        fun compute(
            canvasWidth: Float,
            canvasHeight: Float,
            topInsetPx: Float = 0f,
            bottomInsetPx: Float = 0f,
            leftInsetPx: Float = 0f,
            rightInsetPx: Float = 0f,
        ): CourtViewportMetrics {
            val safeW = max(1f, canvasWidth)
            val safeH = max(1f, canvasHeight)
            val padLeft = leftInsetPx.coerceIn(0f, safeW * 0.4f)
            val padRight = rightInsetPx.coerceIn(0f, safeW * 0.4f)
            val padTop = topInsetPx.coerceIn(0f, safeH * 0.4f)
            val padBottom = bottomInsetPx.coerceIn(0f, safeH * 0.4f)

            val availW = max(1f, safeW - padLeft - padRight)
            val availH = max(1f, safeH - padTop - padBottom)
            val viewAspect = VIEW_WIDTH_FT / VIEW_HEIGHT_FT
            val availAspect = availW / availH

            val drawW: Float
            val drawH: Float
            val viewLeftPx: Float
            val viewTopPx: Float

            if (availAspect > viewAspect) {
                drawH = availH
                drawW = availH * viewAspect
                viewLeftPx = padLeft + (availW - drawW) / 2f
                viewTopPx = padTop
            } else {
                drawW = availW
                drawH = availW / viewAspect
                viewLeftPx = padLeft
                viewTopPx = padTop + (availH - drawH) / 2f
            }

            val scale = drawW / VIEW_WIDTH_FT
            val offsetX = viewLeftPx - VIEW_MIN_X * scale
            val offsetY = viewTopPx - (CourtSpec.WORLD_HEIGHT_FT - VIEW_MAX_Y) * scale

            return CourtViewportMetrics(
                width = safeW,
                height = safeH,
                drawW = drawW,
                drawH = drawH,
                offsetX = offsetX,
                offsetY = offsetY,
                scale = scale,
            )
        }
    }
}

class Particle(
    var x: Float = 0f,
    var y: Float = 0f,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var life: Float = 0f,
    var maxLife: Float = 0.3f,
    var color: Color = GoldBall,
)

data class CourtRenderScene(
    val rallyPhase: RallyPhase,
    val serveAssignment: ServeAssignment,
    val mirrorServeHighlight: Boolean,
    val lastFaultReason: FaultReason?,
    val upperPlayers: List<PlayerEntity>,
    val lowerPlayers: List<PlayerEntity>,
    val primaryLowerIndex: Int,
    val ball: BallState,
    val inReach: Boolean,
    val hitState: PlayerHitState,
    val showDimensions: Boolean,
    val elapsedSec: Float,
    val shakeX: Float,
    val shakeY: Float,
    val swipeActive: Boolean,
    val swipeStartX: Float,
    val swipeStartY: Float,
    val swipeCurX: Float,
    val swipeCurY: Float,
    val particles: List<Particle>,
    val lastCallout: String,
    val calloutAlpha: Float,
    val topInsetPx: Float = 0f,
    val bottomInsetPx: Float = 0f,
    val leftInsetPx: Float = 0f,
    val rightInsetPx: Float = 0f,
)

fun DrawScope.drawCourtScene(scene: CourtRenderScene) {
    val m = CourtViewportMetrics.compute(
        canvasWidth = size.width,
        canvasHeight = size.height,
        topInsetPx = scene.topInsetPx,
        bottomInsetPx = scene.bottomInsetPx,
        leftInsetPx = scene.leftInsetPx,
        rightInsetPx = scene.rightInsetPx,
    )
    val dprScale = m.scale / 14f

    drawRect(color = CourtApron, size = size)

    translate(left = scene.shakeX * m.scale, top = scene.shakeY * m.scale) {
        val courtTopLeft = m.worldToScreen(CourtSpec.COURT_LEFT_X, CourtSpec.COURT_TOP_Y)
        val courtW = CourtSpec.COURT_WIDTH_FT * m.scale
        val courtH = CourtSpec.COURT_LENGTH_FT * m.scale

        // Playing surface
        drawRect(
            color = CourtBlue,
            topLeft = courtTopLeft,
            size = Size(courtW, courtH),
        )

        // Kitchen / Non-Volley Zone
        val nvzTopLeft = m.worldToScreen(CourtSpec.COURT_LEFT_X, CourtSpec.UPPER_NVZ_Y)
        val nvzH = CourtSpec.KITCHEN_DEPTH_FT * 2f * m.scale
        drawRect(
            color = KitchenBlue,
            topLeft = nvzTopLeft,
            size = Size(courtW, nvzH),
        )

        // Serve target box highlight
        if (scene.rallyPhase == RallyPhase.WAITING_SERVE) {
            val assign = scene.serveAssignment
            val targetCourtSide = if (scene.mirrorServeHighlight) {
                if (assign.targetCourtSide == CourtSide.UPPER) CourtSide.LOWER else CourtSide.UPPER
            } else {
                assign.targetCourtSide
            }
            val boxSide = if (scene.mirrorServeHighlight) {
                if (assign.targetBox == ServiceBoxSide.LEFT) ServiceBoxSide.RIGHT else ServiceBoxSide.LEFT
            } else {
                assign.targetBox
            }
            val boxLeftX = if (boxSide == ServiceBoxSide.LEFT) {
                CourtSpec.COURT_LEFT_X
            } else {
                CourtSpec.COURT_CENTER_X
            }
            val boxTopY = if (targetCourtSide == CourtSide.UPPER) {
                CourtSpec.COURT_TOP_Y
            } else {
                CourtSpec.LOWER_NVZ_Y
            }
            val boxPt = m.worldToScreen(boxLeftX, boxTopY)
            drawRect(
                color = LimeAccent.copy(alpha = 0.16f),
                topLeft = boxPt,
                size = Size((CourtSpec.COURT_WIDTH_FT / 2f) * m.scale, CourtSpec.SERVICE_DEPTH_FT * m.scale),
            )
        }

        // Kitchen fault highlight
        if (scene.rallyPhase == RallyPhase.POINT_PAUSE && scene.lastFaultReason == FaultReason.KITCHEN_VOLLEY) {
            val lowerNvzTop = m.worldToScreen(CourtSpec.COURT_LEFT_X, CourtSpec.NET_Y)
            drawRect(
                color = Color(0xFFDC2626).copy(alpha = 0.28f),
                topLeft = lowerNvzTop,
                size = Size(courtW, CourtSpec.KITCHEN_DEPTH_FT * m.scale),
            )
        }

        // Court lines
        val lw = max(2.5f, CourtSpec.LINE_WIDTH_FT * m.scale)
        val halfLw = lw / 2f
        drawRect(LineWhite, Offset(courtTopLeft.x - halfLw, courtTopLeft.y - halfLw), Size(lw, courtH + lw))
        drawRect(LineWhite, Offset(courtTopLeft.x + courtW - halfLw, courtTopLeft.y - halfLw), Size(lw, courtH + lw))
        drawRect(LineWhite, Offset(courtTopLeft.x - halfLw, courtTopLeft.y - halfLw), Size(courtW + lw, lw))
        drawRect(LineWhite, Offset(courtTopLeft.x - halfLw, courtTopLeft.y + courtH - halfLw), Size(courtW + lw, lw))

        val upperNvzPt = m.worldToScreen(CourtSpec.COURT_LEFT_X, CourtSpec.UPPER_NVZ_Y)
        val lowerNvzPt = m.worldToScreen(CourtSpec.COURT_LEFT_X, CourtSpec.LOWER_NVZ_Y)
        drawRect(LineWhite, Offset(upperNvzPt.x, upperNvzPt.y - halfLw), Size(courtW, lw))
        drawRect(LineWhite, Offset(lowerNvzPt.x, lowerNvzPt.y - halfLw), Size(courtW, lw))

        val centerTop = m.worldToScreen(CourtSpec.COURT_CENTER_X, CourtSpec.COURT_TOP_Y)
        val centerLowerNvz = m.worldToScreen(CourtSpec.COURT_CENTER_X, CourtSpec.LOWER_NVZ_Y)
        val serviceH = CourtSpec.SERVICE_DEPTH_FT * m.scale
        drawRect(LineWhite, Offset(centerTop.x - halfLw, centerTop.y), Size(lw, serviceH))
        drawRect(LineWhite, Offset(centerLowerNvz.x - halfLw, centerLowerNvz.y), Size(lw, serviceH))

        // Dimensions overlay
        if (scene.showDimensions) {
            val wMid = m.worldToScreen(CourtSpec.COURT_CENTER_X, CourtSpec.COURT_TOP_Y + 1.8f)
            drawDimensionTag(wMid.x, wMid.y, "20 ft", dprScale)
            val lMid = m.worldToScreen(CourtSpec.COURT_RIGHT_X + 1.7f, CourtSpec.NET_Y + 6f)
            drawDimensionTag(lMid.x, lMid.y, "44 ft", dprScale)
            val nvzTag = m.worldToScreen(CourtSpec.COURT_LEFT_X + 3.4f, CourtSpec.LOWER_NVZ_Y + 3.5f)
            drawDimensionTag(nvzTag.x, nvzTag.y, "7 ft NVZ", dprScale)
        }

        // Upper team / opponent
        scene.upperPlayers.forEach { entity ->
            drawCourtPlayer(
                entity = entity,
                m = m,
                inReach = false,
                hitState = PlayerHitState.NONE,
                showHitRingGuide = false,
                elapsedSec = scene.elapsedSec,
            )
        }

        // Net with crisp shadow, mesh band, and bright top tape
        val netLeft = m.worldToScreen(CourtSpec.COURT_LEFT_X - CourtSpec.NET_POST_OFFSET_FT, CourtSpec.NET_Y)
        val netRight = m.worldToScreen(CourtSpec.COURT_RIGHT_X + CourtSpec.NET_POST_OFFSET_FT, CourtSpec.NET_Y)
        val netSpan = netRight.x - netLeft.x
        drawRect(Color(0x66060913), Offset(netLeft.x, netLeft.y + 0.10f * m.scale), Size(netSpan, 0.42f * m.scale))
        drawRect(Color(0xFF1E293B).copy(alpha = 0.85f), Offset(netLeft.x, netLeft.y - 0.16f * m.scale), Size(netSpan, 0.34f * m.scale))
        drawRect(LineWhite, Offset(netLeft.x, netLeft.y - 0.08f * m.scale), Size(netSpan, max(2.5f, 0.15f * m.scale)))
        val postRadius = max(4f, 0.32f * m.scale)
        drawCircle(Color(0xFF0F172A), postRadius, Offset(netLeft.x, netLeft.y))
        drawCircle(Color(0xFF94A3B8), postRadius * 0.55f, Offset(netLeft.x, netLeft.y))
        drawCircle(Color(0xFF0F172A), postRadius, Offset(netRight.x, netLeft.y))
        drawCircle(Color(0xFF94A3B8), postRadius * 0.55f, Offset(netRight.x, netLeft.y))

        // Lower team / player
        scene.lowerPlayers.forEachIndexed { idx, entity ->
            val isPrimary = idx == scene.primaryLowerIndex
            drawCourtPlayer(
                entity = entity,
                m = m,
                inReach = if (isPrimary) scene.inReach else false,
                hitState = if (isPrimary) scene.hitState else PlayerHitState.NONE,
                showHitRingGuide = isPrimary,
                elapsedSec = scene.elapsedSec,
            )
        }

        // Active swipe aim guide
        if (scene.swipeActive && hypot(scene.swipeCurX - scene.swipeStartX, scene.swipeCurY - scene.swipeStartY) > 0.35f) {
            val s0 = m.worldToScreen(scene.swipeStartX, scene.swipeStartY)
            val s1 = m.worldToScreen(scene.swipeCurX, scene.swipeCurY)
            drawLine(
                color = LimeAccent.copy(alpha = 0.72f),
                start = s0,
                end = s1,
                strokeWidth = max(2f, 0.12f * m.scale),
            )
        }

        // Particles
        scene.particles.forEach { p ->
            if (p.life > 0f) {
                val sp = m.worldToScreen(p.x, p.y)
                drawCircle(
                    color = p.color.copy(alpha = (p.life / p.maxLife).coerceIn(0f, 1f)),
                    radius = max(1.5f, 0.13f * m.scale),
                    center = sp,
                )
            }
        }

        // Ball height guide, shadow & ball (visually scaled for crisp readability)
        val ball = scene.ball
        val groundPt = m.worldToScreen(ball.x, ball.y)
        val visualBallPt = m.worldToScreen(ball.x, ball.y + ball.z * 0.32f)
        val ballScale = spriteScaleForHeight(ball.z)
        val ballRadiusPx = max(6.5f, 0.29f * ballScale * m.scale)

        if (ball.z > 2.2f) {
            drawLine(
                color = GoldBall.copy(alpha = 0.28f),
                start = groundPt,
                end = visualBallPt,
                strokeWidth = max(1.5f, 0.07f * m.scale),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.18f * m.scale, 0.18f * m.scale)),
            )
        }

        val shadowAlpha = (0.48f - ball.z * 0.025f).coerceIn(0.16f, 0.48f)
        val shadowRadius = max(4.5f, 0.26f * (1f + ball.z * 0.04f) * m.scale)
        drawCircle(Color(0xFF080C18).copy(alpha = shadowAlpha), shadowRadius, groundPt)

        drawCircle(GoldBall, ballRadiusPx, visualBallPt)
        drawCircle(Color(0xFF0F172A), ballRadiusPx, visualBallPt, style = Stroke(width = max(2.2f, 0.09f * m.scale)))
        drawCircle(
            color = Color(0xFFFEF08A),
            radius = ballRadiusPx * 0.32f,
            center = Offset(visualBallPt.x - ballRadiusPx * 0.26f, visualBallPt.y - ballRadiusPx * 0.26f),
        )

        // Callout badge
        if (scene.calloutAlpha > 0.02f && scene.lastCallout.isNotBlank()) {
            val alpha = min(1f, scene.calloutAlpha * 1.25f)
            val callPt = m.worldToScreen(CourtSpec.COURT_CENTER_X, CourtSpec.NET_Y + 0.5f)
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.White.copy(alpha = alpha).toArgb()
                textSize = 13f * dprScale
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val tw = textPaint.measureText(scene.lastCallout)
            val boxW = tw + 18f * dprScale
            val boxH = 26f * dprScale
            val isFault = scene.lastCallout.contains("fault", ignoreCase = true) ||
                scene.lastCallout.contains("Net!") ||
                scene.lastCallout.contains("Out!") ||
                scene.lastCallout.contains("Double bounce!")
            val fillColor = if (isFault) {
                Color(0xFFDC2626).copy(alpha = 0.94f * alpha)
            } else {
                NavyDeep.copy(alpha = 0.90f * alpha)
            }
            val borderColor = if (isFault) {
                Color(0xFFFECACA).copy(alpha = 0.55f * alpha)
            } else {
                LimeAccent.copy(alpha = 0.65f * alpha)
            }

            val topLeft = Offset(callPt.x - boxW / 2f, callPt.y - boxH / 2f)
            val corner = CornerRadius(8f * dprScale, 8f * dprScale)
            drawRoundRect(fillColor, topLeft, Size(boxW, boxH), corner)
            drawRoundRect(borderColor, topLeft, Size(boxW, boxH), corner, style = Stroke(width = max(1f, dprScale)))
            drawContext.canvas.nativeCanvas.drawText(
                scene.lastCallout,
                callPt.x,
                callPt.y + 4.5f * dprScale,
                textPaint,
            )
        }
    }
}

private fun DrawScope.drawDimensionTag(sx: Float, sy: Float, label: String, dprScale: Float) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = LineWhite.toArgb()
        textSize = 11.5f * dprScale
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    val tw = paint.measureText(label)
    val boxW = tw + 12f * dprScale
    val boxH = 19f * dprScale
    val tl = Offset(sx - boxW / 2f, sy - boxH / 2f)
    val corner = CornerRadius(4f * dprScale, 4f * dprScale)
    drawRoundRect(NavyDeep.copy(alpha = 0.92f), tl, Size(boxW, boxH), corner)
    drawRoundRect(LineWhite.copy(alpha = 0.35f), tl, Size(boxW, boxH), corner, style = Stroke(width = max(1.2f, dprScale)))
    drawContext.canvas.nativeCanvas.drawText(label, sx, sy + 4.2f * dprScale, paint)
}

private fun DrawScope.drawCourtPlayer(
    entity: PlayerEntity,
    m: CourtViewportMetrics,
    inReach: Boolean,
    hitState: PlayerHitState,
    showHitRingGuide: Boolean,
    elapsedSec: Float,
) {
    val pal = characterPalette(entity.characterId)
    val pos = m.worldToScreen(entity.x, entity.y)
    val facingUp = entity.side == CourtSide.LOWER
    val facingSign = if (facingUp) -1f else 1f

    if (entity.side == CourtSide.LOWER && (inReach || showHitRingGuide || hitState != PlayerHitState.NONE)) {
        val hitBlocked = hitState == PlayerHitState.WAIT_BOUNCE ||
            hitState == PlayerHitState.KITCHEN ||
            hitState == PlayerHitState.TOO_HIGH
        val reachPx = SinglePlayerMatch.PLAYER_REACH_FT * m.scale

        if (hitBlocked) {
            drawCircle(Color(0xFFF97316).copy(alpha = 0.12f), reachPx, pos)
            drawCircle(
                color = Color(0xFFFB923C).copy(alpha = 0.90f),
                radius = reachPx,
                center = pos,
                style = Stroke(
                    width = max(2f, 0.12f * m.scale),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.32f * m.scale, 0.20f * m.scale)),
                ),
            )
            val hint = when (hitState) {
                PlayerHitState.WAIT_BOUNCE -> "Let it bounce"
                PlayerHitState.KITCHEN -> "Step out of the kitchen"
                PlayerHitState.TOO_HIGH -> "Too high"
                else -> ""
            }
            if (hint.isNotEmpty()) {
                val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = OrangeSoft.toArgb()
                    textSize = max(24f, 0.92f * m.scale)
                    textAlign = Paint.Align.CENTER
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                val hintY = pos.y - (SinglePlayerMatch.PLAYER_REACH_FT + 0.55f) * m.scale
                val hw = hintPaint.measureText(hint) + 0.9f * m.scale
                val hh = hintPaint.textSize * 1.45f
                val corner = CornerRadius(0.32f * m.scale, 0.32f * m.scale)
                drawRoundRect(
                    color = NavyDeep.copy(alpha = 0.92f),
                    topLeft = Offset(pos.x - hw / 2f, hintY - hh * 0.72f),
                    size = Size(hw, hh),
                    cornerRadius = corner,
                )
                drawRoundRect(
                    color = Color(0xFFFB923C).copy(alpha = 0.7f),
                    topLeft = Offset(pos.x - hw / 2f, hintY - hh * 0.72f),
                    size = Size(hw, hh),
                    cornerRadius = corner,
                    style = Stroke(width = max(1.5f, 0.06f * m.scale)),
                )
                drawContext.canvas.nativeCanvas.drawText(hint, pos.x, hintY, hintPaint)
            }
        } else if (inReach) {
            val pulse = 0.5f + 0.5f * sin(elapsedSec * 12f)
            val ringRadius = (SinglePlayerMatch.PLAYER_REACH_FT - 0.35f + pulse * 0.35f) * m.scale
            drawCircle(LimeAccent.copy(alpha = 0.14f + pulse * 0.16f), ringRadius, pos)
            drawCircle(
                color = LimeBright.copy(alpha = 0.72f + pulse * 0.28f),
                radius = ringRadius,
                center = pos,
                style = Stroke(
                    width = max(2f, (0.10f + pulse * 0.08f) * m.scale),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.32f * m.scale, 0.20f * m.scale)),
                ),
            )
            val ripplePhase = (elapsedSec * 1.9f) % 1f
            drawCircle(
                color = LimeBright.copy(alpha = (1f - ripplePhase) * 0.55f),
                radius = (SinglePlayerMatch.PLAYER_REACH_FT + ripplePhase * 1.05f) * m.scale,
                center = pos,
                style = Stroke(width = max(1.2f, 0.06f * m.scale)),
            )
        } else {
            drawCircle(
                color = LineWhite.copy(alpha = 0.16f),
                radius = reachPx,
                center = pos,
                style = Stroke(
                    width = max(1f, 0.05f * m.scale),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.28f * m.scale, 0.28f * m.scale)),
                ),
            )
        }
    }

    // Shadow ellipse
    drawOval(
        color = Color(0xFF070B14).copy(alpha = 0.34f),
        topLeft = Offset(pos.x - 1.18f * m.scale, pos.y + 0.32f * m.scale - 0.62f * m.scale),
        size = Size(2.36f * m.scale, 1.24f * m.scale),
    )

    // Paddle
    val swingProg = if (entity.swingTimer > 0f) 1f - entity.swingTimer / 0.24f else 0f
    val baseAngle = if (facingUp) -0.25f else (PI.toFloat() - 0.25f)
    val paddleAngle = baseAngle + (if (facingUp) -1f else 1f) * swingProg * 1.45f
    val paddleDist = 1.48f * m.scale
    val px = pos.x + cos(paddleAngle) * paddleDist
    val py = pos.y + sin(paddleAngle) * paddleDist

    drawLine(
        color = Color(0xFF334155),
        start = Offset(pos.x + cos(paddleAngle) * 0.7f * m.scale, pos.y + sin(paddleAngle) * 0.7f * m.scale),
        end = Offset(px, py),
        strokeWidth = max(2f, 0.2f * m.scale),
    )
    drawCircle(pal.paddle, 0.54f * m.scale, Offset(px, py))
    drawCircle(Color(0xFF0F172A), 0.54f * m.scale, Offset(px, py), style = Stroke(max(1.4f, 0.08f * m.scale)))

    // Shirt body
    drawCircle(pal.shirt, pal.bodyRadiusFt * m.scale, pos)
    drawCircle(Color(0xFF0F172A), pal.bodyRadiusFt * m.scale, pos, style = Stroke(max(1.4f, 0.08f * m.scale)))

    // Head
    val headY = pos.y + facingSign * 0.18f * m.scale
    drawCircle(pal.skin, 0.72f * m.scale, Offset(pos.x, headY))
    drawCircle(Color(0xFF0F172A), 0.72f * m.scale, Offset(pos.x, headY), style = Stroke(max(1.4f, 0.08f * m.scale)))

    if (pal.isPickleMascot) {
        val bumpColor = Color(0xFF3F6212).copy(alpha = 0.55f)
        drawCircle(bumpColor, 0.14f * m.scale, Offset(pos.x - 0.34f * m.scale, headY - facingSign * 0.2f * m.scale))
        drawCircle(bumpColor, 0.12f * m.scale, Offset(pos.x + 0.28f * m.scale, headY - facingSign * 0.12f * m.scale))
    } else {
        drawArc(
            color = pal.hair,
            startAngle = if (facingUp) 0f else 180f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(pos.x - 0.69f * m.scale, headY - facingSign * 0.12f * m.scale - 0.69f * m.scale),
            size = Size(1.38f * m.scale, 1.38f * m.scale),
        )
    }

    if (pal.hasBeard) {
        drawArc(
            color = pal.hair,
            startAngle = if (facingUp) 180f else 0f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(pos.x - 0.48f * m.scale, headY + facingSign * 0.3f * m.scale - 0.48f * m.scale),
            size = Size(0.96f * m.scale, 0.96f * m.scale),
        )
    }

    // Headband
    drawRect(
        color = pal.headwear,
        topLeft = Offset(pos.x - 0.67f * m.scale, headY + facingSign * 0.22f * m.scale - 0.14f * m.scale),
        size = Size(1.34f * m.scale, 0.28f * m.scale),
    )
    if (entity.characterId == CharacterId.RIO) {
        drawRect(
            color = pal.headwear,
            topLeft = Offset(pos.x + 0.52f * m.scale, headY - facingSign * 0.08f * m.scale),
            size = Size(0.32f * m.scale, 0.18f * m.scale),
        )
    }

    if (pal.hasGlasses) {
        val eyeY = headY + facingSign * 0.38f * m.scale
        val stroke = Stroke(width = max(1.2f, 0.07f * m.scale))
        drawRect(Color(0xFF0F172A), Offset(pos.x - 0.42f * m.scale, eyeY - 0.1f * m.scale), Size(0.3f * m.scale, 0.2f * m.scale), style = stroke)
        drawRect(Color(0xFF0F172A), Offset(pos.x + 0.12f * m.scale, eyeY - 0.1f * m.scale), Size(0.3f * m.scale, 0.2f * m.scale), style = stroke)
    }

    val playerLabel = entity.displayName?.takeIf { it.isNotBlank() } ?: pal.name
    val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = LineWhite.toArgb()
        textSize = max(24f, 0.88f * m.scale)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val nameY = pos.y + (if (facingUp) 2.02f else -1.48f) * m.scale
    val nw = namePaint.measureText(playerLabel) + 0.8f * m.scale
    val nh = namePaint.textSize * 1.38f
    drawRoundRect(
        color = NavyDeep.copy(alpha = 0.82f),
        topLeft = Offset(pos.x - nw / 2f, nameY - nh * 0.74f),
        size = Size(nw, nh),
        cornerRadius = CornerRadius(0.28f * m.scale, 0.28f * m.scale),
    )
    drawContext.canvas.nativeCanvas.drawText(
        playerLabel,
        pos.x,
        nameY,
        namePaint,
    )
}
