package com.picklerally.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.picklerally.app.game.CharacterId
import com.picklerally.app.game.characterPalette

@Composable
fun CharacterPortrait(
    characterId: CharacterId,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    locked: Boolean = false,
) {
    val pal = characterPalette(characterId)
    val shirt = if (locked) Color(0xFF334155) else pal.shirt
    val skin = if (locked) Color(0xFF475569) else pal.skin
    val headwear = if (locked) Color(0xFF64748B) else pal.headwear
    val paddle = if (locked) Color(0xFF475569) else pal.paddle
    val hair = if (locked) Color(0xFF334155) else pal.hair
    val bodyScale = if (pal.bigBuild) 1.12f else 1f
    val outlineDark = Color(0xFF0F172A)

    Canvas(
        modifier = modifier
            .size(size)
            .semantics {
                contentDescription = if (locked) "${pal.name} portrait (locked)" else "${pal.name} portrait"
            },
    ) {
        val s = this.size.minDimension / 64f
        fun p(v: Float) = v * s

        // Outer rounded frame
        drawRoundRect(
            color = Color(0xFF0B1220),
            topLeft = Offset(p(2f), p(2f)),
            size = Size(p(60f), p(60f)),
            cornerRadius = CornerRadius(p(12f), p(12f)),
        )
        drawRoundRect(
            color = Color(0xFF1E293B),
            topLeft = Offset(p(2f), p(2f)),
            size = Size(p(60f), p(60f)),
            cornerRadius = CornerRadius(p(12f), p(12f)),
            style = Stroke(width = p(2f)),
        )

        // Paddle handle & face on right
        drawRoundRect(
            color = Color(0xFF78350F),
            topLeft = Offset(p(49f), p(34f)),
            size = Size(p(4f), p(11f)),
            cornerRadius = CornerRadius(p(1.5f), p(1.5f)),
        )
        drawRoundRect(
            color = outlineDark,
            topLeft = Offset(p(49f), p(34f)),
            size = Size(p(4f), p(11f)),
            cornerRadius = CornerRadius(p(1.5f), p(1.5f)),
            style = Stroke(width = p(1.5f)),
        )
        drawRoundRect(
            color = paddle,
            topLeft = Offset(p(44f), p(18f)),
            size = Size(p(13f), p(18f)),
            cornerRadius = CornerRadius(p(4f), p(4f)),
        )
        drawRoundRect(
            color = outlineDark,
            topLeft = Offset(p(44f), p(18f)),
            size = Size(p(13f), p(18f)),
            cornerRadius = CornerRadius(p(4f), p(4f)),
            style = Stroke(width = p(2f)),
        )
        if (!locked) {
            drawRoundRect(
                color = Color.White.copy(alpha = 0.28f),
                topLeft = Offset(p(47f), p(21f)),
                size = Size(p(3f), p(7f)),
                cornerRadius = CornerRadius(p(1f), p(1f)),
            )
        }

        // Shirt / Torso
        scale(scaleX = bodyScale, scaleY = 1f, pivot = Offset(p(32f), 0f)) {
            drawRoundRect(
                color = shirt,
                topLeft = Offset(p(17f), p(37f)),
                size = Size(p(28f), p(16f)),
                cornerRadius = CornerRadius(p(4f), p(4f)),
            )
            drawRoundRect(
                color = outlineDark,
                topLeft = Offset(p(17f), p(37f)),
                size = Size(p(28f), p(16f)),
                cornerRadius = CornerRadius(p(4f), p(4f)),
                style = Stroke(width = p(2f)),
            )
            if (pal.tankTop) {
                drawRect(color = shirt, topLeft = Offset(p(20f), p(37f)), size = Size(p(5f), p(6f)))
                drawRect(color = shirt, topLeft = Offset(p(37f), p(37f)), size = Size(p(5f), p(6f)))
                drawRect(color = skin, topLeft = Offset(p(25f), p(37f)), size = Size(p(12f), p(4f)))
            } else {
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.75f),
                    topLeft = Offset(p(26f), p(42f)),
                    size = Size(p(10f), p(3f)),
                    cornerRadius = CornerRadius(p(1f), p(1f)),
                )
            }
        }

        // Head
        val headX = if (pal.isPickleMascot) 19f else 17f
        val headY = if (pal.isPickleMascot) 10f else 13f
        val headW = if (pal.isPickleMascot) 24f else 28f
        val headH = if (pal.isPickleMascot) 26f else 23f
        val headR = if (pal.isPickleMascot) 9f else 6f
        drawRoundRect(
            color = skin,
            topLeft = Offset(p(headX), p(headY)),
            size = Size(p(headW), p(headH)),
            cornerRadius = CornerRadius(p(headR), p(headR)),
        )
        drawRoundRect(
            color = outlineDark,
            topLeft = Offset(p(headX), p(headY)),
            size = Size(p(headW), p(headH)),
            cornerRadius = CornerRadius(p(headR), p(headR)),
            style = Stroke(width = p(2f)),
        )

        // Pickle bumps
        if (pal.isPickleMascot && !locked) {
            val bumpColor = Color(0xFF3F6212).copy(alpha = 0.55f)
            drawCircle(color = bumpColor, radius = p(1.7f), center = Offset(p(23f), p(28f)))
            drawCircle(color = bumpColor, radius = p(1.9f), center = Offset(p(38f), p(29f)))
            drawCircle(color = bumpColor, radius = p(1.4f), center = Offset(p(35f), p(14f)))
        }

        // Hair
        if (!pal.isPickleMascot) {
            val hairPath = Path().apply {
                moveTo(p(17f), p(19f))
                cubicTo(p(17f), p(11f), p(45f), p(11f), p(45f), p(19f))
                lineTo(p(45f), p(21f))
                lineTo(p(17f), p(21f))
                close()
            }
            drawPath(path = hairPath, color = hair)
            drawPath(path = hairPath, color = outlineDark, style = Stroke(width = p(1.5f)))
        }

        // Headband / Visor
        drawRoundRect(
            color = headwear,
            topLeft = Offset(p(16f), p(16f)),
            size = Size(p(30f), p(5f)),
            cornerRadius = CornerRadius(p(2f), p(2f)),
        )
        drawRoundRect(
            color = outlineDark,
            topLeft = Offset(p(16f), p(16f)),
            size = Size(p(30f), p(5f)),
            cornerRadius = CornerRadius(p(2f), p(2f)),
            style = Stroke(width = p(1.5f)),
        )
        if (characterId == CharacterId.LOLA) {
            drawRoundRect(
                color = Color(0xFFF472B6),
                topLeft = Offset(p(21f), p(20f)),
                size = Size(p(20f), p(3f)),
                cornerRadius = CornerRadius(p(1f), p(1f)),
            )
            drawRoundRect(
                color = outlineDark,
                topLeft = Offset(p(21f), p(20f)),
                size = Size(p(20f), p(3f)),
                cornerRadius = CornerRadius(p(1f), p(1f)),
                style = Stroke(width = p(1f)),
            )
        }
        if (characterId == CharacterId.RIO || characterId == CharacterId.DILL) {
            drawRoundRect(
                color = Color.White.copy(alpha = 0.85f),
                topLeft = Offset(p(29f), p(17f)),
                size = Size(p(4f), p(3f)),
                cornerRadius = CornerRadius(p(0.5f), p(0.5f)),
            )
        }

        // Eyes
        drawRoundRect(
            color = outlineDark,
            topLeft = Offset(p(24f), p(24f)),
            size = Size(p(3f), p(4.5f)),
            cornerRadius = CornerRadius(p(1f), p(1f)),
        )
        drawRoundRect(
            color = outlineDark,
            topLeft = Offset(p(35f), p(24f)),
            size = Size(p(3f), p(4.5f)),
            cornerRadius = CornerRadius(p(1f), p(1f)),
        )

        // Glasses
        if (pal.hasGlasses) {
            val lensFill = Color(0xFF38BDF8).copy(alpha = 0.18f)
            drawRoundRect(
                color = lensFill,
                topLeft = Offset(p(21.5f), p(22.5f)),
                size = Size(p(7.5f), p(6.5f)),
                cornerRadius = CornerRadius(p(1.5f), p(1.5f)),
            )
            drawRoundRect(
                color = outlineDark,
                topLeft = Offset(p(21.5f), p(22.5f)),
                size = Size(p(7.5f), p(6.5f)),
                cornerRadius = CornerRadius(p(1.5f), p(1.5f)),
                style = Stroke(width = p(1.6f)),
            )
            drawRoundRect(
                color = lensFill,
                topLeft = Offset(p(33f), p(22.5f)),
                size = Size(p(7.5f), p(6.5f)),
                cornerRadius = CornerRadius(p(1.5f), p(1.5f)),
            )
            drawRoundRect(
                color = outlineDark,
                topLeft = Offset(p(33f), p(22.5f)),
                size = Size(p(7.5f), p(6.5f)),
                cornerRadius = CornerRadius(p(1.5f), p(1.5f)),
                style = Stroke(width = p(1.6f)),
            )
            drawLine(
                color = outlineDark,
                start = Offset(p(29f), p(25.5f)),
                end = Offset(p(33f), p(25.5f)),
                strokeWidth = p(1.6f),
            )
        }

        // Beard or Smile
        if (pal.hasBeard) {
            val beardPath = Path().apply {
                moveTo(p(19f), p(29f))
                lineTo(p(43f), p(29f))
                lineTo(p(41f), p(37f))
                quadraticTo(p(31f), p(41f), p(21f), p(37f))
                close()
            }
            drawPath(path = beardPath, color = hair)
            drawPath(path = beardPath, color = outlineDark, style = Stroke(width = p(1.4f)))
        } else {
            drawRoundRect(
                color = Color(0xFF9A3412),
                topLeft = Offset(p(28f), p(31f)),
                size = Size(p(6f), p(2f)),
                cornerRadius = CornerRadius(p(1f), p(1f)),
            )
        }
    }
}
