package io.github.hobin66.webdavplayer.ui.components.slider

import android.content.Context
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MusicNote
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
import io.github.hobin66.webdavplayer.R
import io.github.hobin66.webdavplayer.lib.domain.CurrentItemTimerOption
import io.github.hobin66.webdavplayer.lib.domain.DurationTimerOption
import io.github.hobin66.webdavplayer.lib.domain.DurationTimerStopMode
import io.github.hobin66.webdavplayer.lib.domain.LibraryType
import io.github.hobin66.webdavplayer.lib.domain.LibraryType.LIBRARY
import io.github.hobin66.webdavplayer.lib.domain.TimerOption
import kotlin.math.roundToInt

@Composable
fun SleepTimerSlider(
  context: Context,
  libraryType: LibraryType,
  option: TimerOption?,
  durationStopMode: DurationTimerStopMode,
  modifier: Modifier = Modifier,
  onUpdate: (TimerOption?) -> Unit,
) {
  val sliderRange = INTERNAL_MIN_VALUE..INTERNAL_MAX_VALUE
  val floatRange = sliderRange.first.toFloat()..sliderRange.last.toFloat()

  val onValueUpdate: (Float) -> Unit = { value ->
    onUpdate(timerOptionFromSliderValue(value.coerceIn(floatRange), durationStopMode))
  }

  val sliderState =
    rememberSaveable(durationStopMode, saver = SliderState.saver(onValueUpdate)) {
      SliderState(
        current = option.toInternalValue(),
        bounds = sliderRange,
        onUpdate = onValueUpdate,
      )
    }

  LaunchedEffect(Unit) {
    sliderState.snapTo(sliderState.current)
  }

  LaunchedEffect(option) {
    sliderState.animateDecayTo(option.toInternalValue().toFloat().coerceIn(floatRange))
  }

  val clampedCurrent = sliderState.current.coerceIn(floatRange)

  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
      text = clampedCurrent.toLabelText(libraryType, context),
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
      val segmentPixelWidth = constraints.maxWidth.toFloat() / visibleSegments
      val visibleSegmentCount = (visibleSegments + 1) / 2

      val minIndex =
        (clampedCurrent - visibleSegmentCount)
          .roundToInt()
          .coerceAtLeast(sliderRange.first)

      val maxIndex =
        (clampedCurrent + visibleSegmentCount)
          .roundToInt()
          .coerceAtMost(sliderRange.last)

      val centerPixel = constraints.maxWidth / 2f

      for (index in minIndex..maxIndex) {
        SpeedSliderSegment(
          index = index,
          currentValue = clampedCurrent,
          segmentWidth = segmentWidth,
          segmentPixelWidth = segmentPixelWidth,
          centerPixel = centerPixel,
          barColor = colorScheme.onSurface,
          formatIndex = { index.toLabelIcon() },
          labeledIndexes = labeledIndexes,
        )
      }
    }
  }
}

private fun TimerOption?.toInternalValue(): Int =
  when (this) {
    null -> INTERNAL_DISABLED
    is DurationTimerOption -> duration.coerceIn(1, INTERNAL_MAX_VALUE)
    CurrentItemTimerOption -> INTERNAL_CHAPTER_END
  }

internal fun timerOptionFromSliderValue(
  value: Float,
  stopMode: DurationTimerStopMode,
): TimerOption? {
  val rounded = value.roundToInt().coerceIn(INTERNAL_MIN_VALUE, INTERNAL_MAX_VALUE)

  return when (rounded) {
    INTERNAL_DISABLED -> null
    INTERNAL_CHAPTER_END -> CurrentItemTimerOption
    else -> DurationTimerOption(duration = rounded, stopMode = stopMode)
  }
}

private fun Float.toLabelText(
  libraryType: LibraryType,
  context: Context,
): String {
  val value = roundToInt().coerceIn(INTERNAL_MIN_VALUE, INTERNAL_MAX_VALUE)

  return when (value) {
    INTERNAL_DISABLED -> {
      context.getString(R.string.timer_option_disabled)
    }

    INTERNAL_CHAPTER_END -> {
      when (libraryType) {
        LIBRARY -> context.getString(R.string.timer_option_after_current_chapter)
        else -> context.getString(R.string.timer_option_after_current_item)
      }
    }

    else -> {
      context.resources.getQuantityString(
        R.plurals.timer_option_after_time,
        value,
        value,
      )
    }
  }
}

private fun Int.toLabelIcon(): Any =
  when (this) {
    INTERNAL_DISABLED -> Icons.Outlined.Close
    INTERNAL_CHAPTER_END -> Icons.Outlined.MusicNote
    else -> this
  }

private const val INTERNAL_MIN_VALUE = -1
private const val INTERNAL_MAX_VALUE = 120

private const val INTERNAL_DISABLED = 0
private const val INTERNAL_CHAPTER_END = -1

private const val visibleSegments = 12

private val labeledIndexes =
  listOf(
    INTERNAL_CHAPTER_END,
    INTERNAL_DISABLED,
  ) + (5..INTERNAL_MAX_VALUE step 5)
