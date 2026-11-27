package com.picklerally.app.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.picklerally.app.R
import kotlinx.coroutines.delay
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun SplashOverlay(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var filledBlocks by remember { mutableStateOf(0) }
    var fading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // Time-based (not animator-based) so it still moves when system animations are off
        withFrameNanos { }
        delay(150L)
        for (b in 1..15) {
            delay(135L)
            filledBlocks = b
        }
        delay(250L)
        fading = true
        delay(450L)
        onFinished()
    }
    val overlayAlpha by animateFloatAsState(
        targetValue = if (fading) 0f else 1f,
        animationSpec = tween(durationMillis = 450),
        label = "splashAlpha",
    )

    val skipDesc = stringResource(R.string.skip_loading)
    val splashBitmap = ImageBitmap.imageResource(id = R.drawable.loading_screen)
    val splashDesc = stringResource(R.string.splash_image_desc)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = overlayAlpha }
            .background(Color(0xFF14213D))
            .testTag("splash_overlay")
            .semantics { contentDescription = skipDesc }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onFinished,
            ),
        contentAlignment = Alignment.Center,
    ) {
        val artAspect = 780f / 1688f
        val maxW = constraints.maxWidth.toFloat()
        val maxH = constraints.maxHeight.toFloat()
        val boxW: Float
        val boxH: Float
        if (maxW / maxH > artAspect) {
            boxH = maxH
            boxW = maxH * artAspect
        } else {
            boxW = maxW
            boxH = maxW / artAspect
        }
        val boxWDp = with(androidx.compose.ui.platform.LocalDensity.current) { boxW.toDp() }
        val boxHDp = with(androidx.compose.ui.platform.LocalDensity.current) { boxH.toDp() }

        Box(
            modifier = Modifier
                .width(boxWDp)
                .height(boxHDp),
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = splashDesc },
            ) {
                drawImage(
                    image = splashBitmap,
                    dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                    filterQuality = FilterQuality.None,
                )
            }

            // Covers the static bar inside the artwork and draws the 15-block animated progress bar
            val trackX = (boxW * 0.1436f).roundToInt()
            val trackY = (boxH * 0.7731f).roundToInt()
            val trackWDp = with(androidx.compose.ui.platform.LocalDensity.current) { (boxW * 0.7141f).toDp() }
            val trackHDp = with(androidx.compose.ui.platform.LocalDensity.current) { (boxH * 0.0290f).toDp() }

            Canvas(
                modifier = Modifier
                    .offset { IntOffset(trackX, trackY) }
                    .width(trackWDp)
                    .height(trackHDp),
            ) {
                drawRect(color = Color(0xFF0A1222))

                val innerLeft = size.width * 0.0144f
                val innerWidth = size.width * 0.9551f
                val innerTop = size.height * 0.184f
                val innerHeight = size.height * 0.633f

                val totalBlocks = 15
                val visibleBlocks = filledBlocks.coerceIn(0, totalBlocks)
                val cellWidth = innerWidth / totalBlocks
                val blockWidth = cellWidth * 0.778f

                for (i in 0 until visibleBlocks) {
                    drawRect(
                        color = Color(0xFF7FB241),
                        topLeft = Offset(innerLeft + i * cellWidth, innerTop),
                        size = Size(blockWidth, innerHeight),
                    )
                }
            }
        }
    }
}
