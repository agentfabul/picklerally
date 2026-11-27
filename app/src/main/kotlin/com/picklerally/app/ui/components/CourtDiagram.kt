package com.picklerally.app.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun CourtDiagram(modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .width(168.dp)
            .height(272.dp)
            .semantics { contentDescription = "Pickleball court diagram" },
    ) {
        val sx = size.width / 120f
        val sy = size.height / 225f
        val s = minOf(sx, sy)
        val ox = (size.width - 120f * s) / 2f
        val oy = (size.height - 225f * s) / 2f

        fun px(x: Float) = ox + x * s
        fun py(y: Float) = oy + y * s

        // Outer court blue
        drawRoundRect(
            color = Color(0xFF1D4ED8),
            topLeft = Offset(px(10f), py(10f)),
            size = Size(100f * s, 200f * s),
            cornerRadius = CornerRadius(2f * s, 2f * s),
        )
        // Kitchen / NVZ
        drawRect(
            color = Color(0xFF1E40AF),
            topLeft = Offset(px(10f), py(75f)),
            size = Size(100f * s, 70f * s),
        )
        // Court outline
        drawRoundRect(
            color = Color(0xFFF8FAFC),
            topLeft = Offset(px(10f), py(10f)),
            size = Size(100f * s, 200f * s),
            cornerRadius = CornerRadius(2f * s, 2f * s),
            style = Stroke(width = 1.6f * s),
        )
        // NVZ lines
        drawLine(Color(0xFFF8FAFC), Offset(px(10f), py(75f)), Offset(px(110f), py(75f)), 1.2f * s)
        drawLine(Color(0xFFF8FAFC), Offset(px(10f), py(145f)), Offset(px(110f), py(145f)), 1.2f * s)
        // Center service lines
        drawLine(Color(0xFFF8FAFC), Offset(px(60f), py(10f)), Offset(px(60f), py(75f)), 1.2f * s)
        drawLine(Color(0xFFF8FAFC), Offset(px(60f), py(145f)), Offset(px(60f), py(210f)), 1.2f * s)
        // Net
        drawLine(Color(0xFFF8FAFC), Offset(px(4f), py(110f)), Offset(px(116f), py(110f)), 2.8f * s)

        // Diagonal serve trajectory
        drawLine(
            color = Color(0xFF84CC16),
            start = Offset(px(85f), py(200f)),
            end = Offset(px(35f), py(30f)),
            strokeWidth = 1.8f * s,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f * s, 3f * s)),
        )
        drawCircle(
            color = Color(0xFF84CC16),
            radius = 3.2f * s,
            center = Offset(px(85f), py(200f)),
        )

        // Labels via nativeCanvas
        val nc = drawContext.canvas.nativeCanvas
        val kitchenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color(0xFFFDE68A).toArgb()
            textAlign = Paint.Align.CENTER
            textSize = 9.5f * s
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        nc.drawText("KITCHEN", px(60f), py(95f), kitchenPaint)
        nc.drawText("KITCHEN", px(60f), py(132f), kitchenPaint)

        val netPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color(0xFFF8FAFC).toArgb()
            textAlign = Paint.Align.CENTER
            textSize = 7.5f * s
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        nc.drawText("NET", px(60f), py(107f), netPaint)

        val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color(0xFFF1F5F9).toArgb()
            textAlign = Paint.Align.CENTER
            textSize = 8.5f * s
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        nc.drawText("Left", px(35f), py(182f), boxPaint)
        nc.drawText("Right", px(85f), py(182f), boxPaint)
        nc.drawText("Right", px(35f), py(42f), boxPaint)
        nc.drawText("Left", px(85f), py(42f), boxPaint)

        val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color(0xFFA3E635).toArgb()
            textAlign = Paint.Align.CENTER
            textSize = 7.8f * s
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        nc.drawText("Serve diagonally, past the kitchen", px(60f), py(222f), hintPaint)
    }
}
