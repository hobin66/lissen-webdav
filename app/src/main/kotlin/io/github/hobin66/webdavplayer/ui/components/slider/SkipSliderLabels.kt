package io.github.hobin66.webdavplayer.ui.components.slider

val SkipSliderRange = 0..60

val SkipSliderLabeledIndexes = (SkipSliderRange.first..SkipSliderRange.last step 5).toList()

fun formatSkipSecondsLabel(
  seconds: Int,
  offLabel: String,
): String {
  val clampedSeconds = seconds.coerceIn(SkipSliderRange)

  return when (clampedSeconds) {
    0 -> offLabel
    else -> "$clampedSeconds s"
  }
}
