package com.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun StudioLiveWaveform(
    amplitudes: List<Float>,
    primaryColor: Color,
    secondaryColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(92.dp)
    ) {
        val barCount = 36
        val totalGap = 4.dp.toPx() * (barCount - 1)
        val barWidth = ((size.width - totalGap) / barCount).coerceAtLeast(2.dp.toPx())
        val step = barWidth + 4.dp.toPx()
        val centerY = size.height / 2f

        // Subtle studio baseline
        drawLine(
            color = primaryColor.copy(alpha = 0.16f),
            start = Offset(0f, centerY),
            end = Offset(size.width, centerY),
            strokeWidth = 1.dp.toPx()
        )

        val padded = if (amplitudes.size >= barCount) {
            amplitudes.takeLast(barCount)
        } else {
            List(barCount - amplitudes.size) { 0.06f } + amplitudes
        }

        padded.forEachIndexed { idx, rawAmp ->
            val amp = rawAmp.coerceIn(0.06f, 1f)
            val barHeight = (amp * size.height * 0.86f).coerceAtLeast(6.dp.toPx())
            val x = idx * step
            val top = centerY - barHeight / 2f
            val alpha = 0.35f + (idx.toFloat() / barCount) * 0.65f

            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = alpha),
                        secondaryColor.copy(alpha = alpha * 0.85f)
                    )
                ),
                topLeft = Offset(x, top),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

@Composable
fun ScrubbableAudioWaveform(
    seedKey: Int,
    durationMs: Long,
    progress: Float,
    isPlaying: Boolean,
    activeColor: Color,
    inactiveColor: Color,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    barCount: Int = 34
) {
    val bars = remember(seedKey, durationMs, barCount) {
        val rng = Random(seedKey * 31L + durationMs)
        List(barCount) { idx ->
            val wave = abs(sin((idx + 1) * 0.42 + seedKey)).toFloat()
            val jitter = rng.nextFloat() * 0.45f
            (0.22f + wave * 0.48f + jitter * 0.30f).coerceIn(0.18f, 1f)
        }
    }

    val clampedProgress = progress.coerceIn(0f, 1f)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(34.dp)
            .pointerInput(seedKey) {
                detectTapGestures { offset ->
                    if (size.width > 0) {
                        onSeek((offset.x / size.width.toFloat()).coerceIn(0f, 1f))
                    }
                }
            }
            .pointerInput(seedKey) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    if (size.width > 0) {
                        onSeek((change.position.x / size.width.toFloat()).coerceIn(0f, 1f))
                    }
                }
            }
    ) {
        val gap = 2.5.dp.toPx()
        val barWidth = ((size.width - gap * (bars.size - 1)) / bars.size).coerceAtLeast(2.dp.toPx())
        val step = barWidth + gap
        val centerY = size.height / 2f

        bars.forEachIndexed { idx, amp ->
            val ratio = (idx + 0.5f) / bars.size.toFloat()
            val isPlayed = ratio <= clampedProgress && (isPlaying || clampedProgress > 0f)
            val barHeight = (amp * size.height * 0.85f).coerceAtLeast(5.dp.toPx())
            val x = idx * step
            val top = centerY - barHeight / 2f

            drawRoundRect(
                color = if (isPlayed) activeColor else inactiveColor,
                topLeft = Offset(x, top),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }

        if (isPlaying || clampedProgress > 0f) {
            val playheadX = (clampedProgress * size.width).coerceIn(1.dp.toPx(), size.width - 1.dp.toPx())
            drawLine(
                color = activeColor,
                start = Offset(playheadX, 2.dp.toPx()),
                end = Offset(playheadX, size.height - 2.dp.toPx()),
                strokeWidth = 2.dp.toPx()
            )
        }
    }
}
