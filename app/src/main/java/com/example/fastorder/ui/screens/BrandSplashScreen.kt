package com.example.fastorder.ui.screens

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fastorder.R
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * In-app branded launch screen - the Zomato/Zepto style moment.
 *
 * This is deliberately separate from the *system* splash (which only supports
 * an icon on a background, and masks that icon to a circle). Anything with
 * text, or artwork outside the icon circle, has to live here.
 *
 * Sequence: logo fades and scales in -> the wordmark rises -> a line sweeps out
 * from the centre and then carries a travelling highlight -> the tagline
 * appears word by word -> [onFinished] fires.
 *
 * @param started gates the animation. This composable is deliberately composed
 *   *before* the system splash is dismissed, so its background paints the first
 *   frame and there is no white flash. But the animation must not begin until
 *   the system splash is actually gone, otherwise the whole sequence plays
 *   behind it and the user sees a fully-drawn, static screen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BrandSplashScreen(
    started: Boolean,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = colorResource(R.color.splash_background)
    val accent = colorResource(R.color.brand_primary)
    val onBackground = colorResource(R.color.splash_on_background)

    val density = LocalDensity.current
    val nameRisePx = with(density) { NAME_RISE_DP.dp.toPx() }
    val wordRisePx = with(density) { WORD_RISE_DP.dp.toPx() }

    val tagline = stringResource(R.string.splash_tagline)
    val words = remember(tagline) { tagline.split(" ") }

    val logoAlpha = remember { Animatable(0f) }
    val logoScale = remember { Animatable(INITIAL_LOGO_SCALE) }
    val nameProgress = remember { Animatable(0f) }
    val lineProgress = remember { Animatable(0f) }
    val wordProgress = remember(words) { words.map { Animatable(0f) } }

    // Keeps the line alive after the sweep completes, so it reads as a loading
    // indicator rather than a static rule.
    val shimmerTransition = rememberInfiniteTransition(label = "lineShimmer")
    val shimmer by shimmerTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(SHIMMER_PERIOD_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerX",
    )

    LaunchedEffect(started) {
        if (!started) return@LaunchedEffect

        val startedAt = SystemClock.uptimeMillis()

        // Scale runs alongside the fade rather than after it.
        launch {
            logoScale.animateTo(1f, tween(LOGO_SCALE_MS, easing = FastOutSlowInEasing))
        }
        logoAlpha.animateTo(1f, tween(LOGO_FADE_MS))
        nameProgress.animateTo(1f, tween(NAME_RISE_MS, easing = FastOutSlowInEasing))
        lineProgress.animateTo(1f, tween(LINE_SWEEP_MS, easing = FastOutSlowInEasing))

        // Tagline reveals one word at a time. coroutineScope suspends until
        // every word has landed.
        coroutineScope {
            wordProgress.forEachIndexed { index, progress ->
                launch {
                    delay(index * WORD_STAGGER_MS)
                    progress.animateTo(1f, tween(WORD_FADE_MS, easing = FastOutSlowInEasing))
                }
            }
        }

        // Let the finished composition actually be read.
        delay(HOLD_MS)

        // Floor on total on-screen time, independent of the animation timings
        // above - guards against the screen flashing past when animator
        // duration scale is reduced or disabled in Developer Options.
        val elapsed = SystemClock.uptimeMillis() - startedAt
        if (elapsed < MIN_VISIBLE_MS) {
            delay(MIN_VISIBLE_MS - elapsed)
        }

        onFinished()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {

            Image(
                painter = painterResource(R.drawable.ic_splash_logo),
                contentDescription = null, // decorative; app name is read out below
                modifier = Modifier
                    .size(140.dp)
                    .alpha(logoAlpha.value)
                    .scale(logoScale.value),
            )

            Text(
                text = stringResource(R.string.app_name),
                color = onBackground,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                modifier = Modifier
                    .alpha(nameProgress.value)
                    .graphicsLayer {
                        translationY = (1f - nameProgress.value) * nameRisePx
                    },
            )

            Spacer(Modifier.height(18.dp))

            // Sweeps out from the centre over a faint track, then a highlight
            // travels along it for as long as the screen is up.
            Canvas(
                modifier = Modifier
                    .width(LINE_WIDTH.dp)
                    .height(LINE_HEIGHT.dp),
            ) {
                val radius = CornerRadius(size.height / 2f)

                drawRoundRect(
                    color = accent.copy(alpha = 0.18f),
                    cornerRadius = radius,
                )

                val sweptWidth = size.width * lineProgress.value
                drawRoundRect(
                    color = accent,
                    topLeft = Offset((size.width - sweptWidth) / 2f, 0f),
                    size = Size(sweptWidth, size.height),
                    cornerRadius = radius,
                )

                if (lineProgress.value >= 1f) {
                    val bandWidth = size.width * SHIMMER_BAND_FRACTION
                    val bandStart = -bandWidth + (size.width + bandWidth) * shimmer
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.85f),
                                Color.Transparent,
                            ),
                            startX = bandStart,
                            endX = bandStart + bandWidth,
                        ),
                        cornerRadius = radius,
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            // FlowRow so a longer tagline wraps instead of being clipped on
            // narrow screens.
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                words.forEachIndexed { index, word ->
                    val progress = wordProgress[index].value
                    Text(
                        text = word,
                        color = onBackground,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.2.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .alpha(progress)
                            .graphicsLayer {
                                translationY = (1f - progress) * wordRisePx
                            },
                    )
                }
            }
        }
    }
}

private const val INITIAL_LOGO_SCALE = 0.82f
private const val LOGO_FADE_MS = 420
private const val LOGO_SCALE_MS = 640
private const val NAME_RISE_MS = 360
private const val LINE_SWEEP_MS = 650

/** Gap between consecutive words appearing. */
private const val WORD_STAGGER_MS = 95L
private const val WORD_FADE_MS = 300

/** Dwell on the completed screen so the tagline is actually readable. */
private const val HOLD_MS = 550L

/** Hard floor on how long the brand screen stays up, start to finish. */
private const val MIN_VISIBLE_MS = 2400L

private const val LINE_WIDTH = 170
private const val LINE_HEIGHT = 4
private const val NAME_RISE_DP = 10
private const val WORD_RISE_DP = 12

/** One pass of the travelling highlight along the line. */
private const val SHIMMER_PERIOD_MS = 1150
private const val SHIMMER_BAND_FRACTION = 0.3f
