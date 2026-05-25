package org.grakovne.lissen.ui.components.slider

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import kotlin.math.roundToInt

@Composable
fun SkipSecondsSlider(
  seconds: Int,
  offLabel: String,
  modifier: Modifier = Modifier,
  onUpdate: (Int) -> Unit,
) {
  val valueModifier: (Float) -> Unit = { value ->
    onUpdate(value.roundToInt().coerceIn(SkipSliderRange))
  }

  val sliderState =
    rememberSaveable(saver = SliderState.saver(valueModifier)) {
      SliderState(
        current = seconds.coerceIn(SkipSliderRange),
        bounds = SkipSliderRange,
        onUpdate = valueModifier,
      )
    }

  LaunchedEffect(Unit) { sliderState.snapTo(sliderState.current) }
  LaunchedEffect(seconds) { sliderState.animateDecayTo(seconds.coerceIn(SkipSliderRange).toFloat()) }

  val clampedCurrent =
    sliderState.current.coerceIn(
      SkipSliderRange.first.toFloat(),
      SkipSliderRange.last.toFloat(),
    )

  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
      text = formatSkipSecondsLabel(clampedCurrent.roundToInt(), offLabel),
      style = typography.headlineSmall,
    )

    Icon(
      imageVector = Icons.Filled.ArrowDropDown,
      contentDescription = null,
    )

    BoxWithConstraints(
      modifier =
        Modifier
          .fillMaxWidth()
          .sliderDrag(sliderState, visibleSegments),
      contentAlignment = Alignment.TopCenter,
    ) {
      val segmentWidth: Dp = maxWidth / visibleSegments
      val segmentPixelWidth: Float = constraints.maxWidth.toFloat() / visibleSegments
      val visibleSegmentCount = (visibleSegments + 1) / 2
      val minIndex =
        (clampedCurrent - visibleSegmentCount)
          .roundToInt()
          .coerceAtLeast(SkipSliderRange.first)
      val maxIndex =
        (clampedCurrent + visibleSegmentCount)
          .roundToInt()
          .coerceAtMost(SkipSliderRange.last)
      val centerPixel = constraints.maxWidth / 2f

      for (index in minIndex..maxIndex) {
        SpeedSliderSegment(
          index = index,
          currentValue = clampedCurrent,
          segmentWidth = segmentWidth,
          segmentPixelWidth = segmentPixelWidth,
          centerPixel = centerPixel,
          barColor = colorScheme.onSurface,
          formatIndex = { value -> formatSkipSecondsLabel(value, offLabel) },
          labeledIndexes = SkipSliderLabeledIndexes,
        )
      }
    }
  }
}

private const val visibleSegments = 12
