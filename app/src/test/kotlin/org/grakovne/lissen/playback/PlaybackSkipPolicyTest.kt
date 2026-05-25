package org.grakovne.lissen.playback

import org.grakovne.lissen.lib.domain.BookFile
import org.grakovne.lissen.lib.domain.CurrentEpisodeTimerOption
import org.grakovne.lissen.lib.domain.DetailedItem
import org.grakovne.lissen.lib.domain.DurationTimerOption
import org.grakovne.lissen.lib.domain.DurationTimerStopMode
import org.grakovne.lissen.lib.domain.PlayingChapter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlaybackSkipPolicyTest {
  @Test
  fun `book skip settings store distinguishes missing value from explicit zero override`() {
    BookSkipSettingsStore.clear()

    assertEquals(null, BookSkipSettingsStore.get("missing-book"))

    BookSkipSettingsStore.put(
      "book-1",
      BookSkipSettings(introSkipSeconds = 0, outroSkipSeconds = 0),
    )

    assertEquals(
      BookSkipSettings(introSkipSeconds = 0, outroSkipSeconds = 0),
      BookSkipSettingsStore.get("book-1"),
    )

    BookSkipSettingsStore.clear()
  }

  @Test
  fun `book skip settings accessors clamp values to supported range`() {
    val settings = BookSkipSettings(introSkipSeconds = -5, outroSkipSeconds = 77)

    assertEquals(0, settings.normalizedIntroSkipSeconds)
    assertEquals(60, settings.normalizedOutroSkipSeconds)
  }

  @Test
  fun `intro skip applies once when chapter position is below threshold`() {
    val settings = BookSkipSettings(introSkipSeconds = 10, outroSkipSeconds = 0)
    val mediaItemId = "chapter-1"

    assertTrue(
      shouldApplyIntroSkip(
        settings = settings,
        chapterPositionSeconds = 3.0,
        currentMediaItemId = mediaItemId,
        lastAppliedMediaItemId = null,
      ),
    )

    assertFalse(
      shouldApplyIntroSkip(
        settings = settings,
        chapterPositionSeconds = 3.0,
        currentMediaItemId = mediaItemId,
        lastAppliedMediaItemId = mediaItemId,
      ),
    )
  }

  @Test
  fun `skip evaluation only runs during active playback`() {
    assertTrue(shouldEvaluateBookSkip(isPlaying = true))
    assertFalse(shouldEvaluateBookSkip(isPlaying = false))
  }

  @Test
  fun `outro skip applies once when chapter is near end`() {
    val settings = BookSkipSettings(introSkipSeconds = 0, outroSkipSeconds = 8)
    val mediaItemId = "chapter-2"

    assertTrue(
      shouldApplyOutroSkip(
        settings = settings,
        chapterPositionSeconds = 93.0,
        chapterDurationSeconds = 100.0,
        currentMediaItemId = mediaItemId,
        lastAppliedMediaItemId = null,
      ),
    )

    assertFalse(
      shouldApplyOutroSkip(
        settings = settings,
        chapterPositionSeconds = 93.0,
        chapterDurationSeconds = 100.0,
        currentMediaItemId = mediaItemId,
        lastAppliedMediaItemId = mediaItemId,
      ),
    )
  }

  @Test
  fun `zero disables intro and outro independently`() {
    assertFalse(
      shouldApplyIntroSkip(
        settings = BookSkipSettings(introSkipSeconds = 0, outroSkipSeconds = 12),
        chapterPositionSeconds = 2.0,
        currentMediaItemId = "chapter-3",
        lastAppliedMediaItemId = null,
      ),
    )

    assertFalse(
      shouldApplyOutroSkip(
        settings = BookSkipSettings(introSkipSeconds = 12, outroSkipSeconds = 0),
        chapterPositionSeconds = 95.0,
        chapterDurationSeconds = 100.0,
        currentMediaItemId = "chapter-4",
        lastAppliedMediaItemId = null,
      ),
    )
  }

  @Test
  fun `intro skip target resolves to total book position`() {
    assertEquals(
      72.0,
      resolveIntroSkipTargetTotalPosition(
        chapterStartSeconds = 60.0,
        settings = BookSkipSettings(introSkipSeconds = 12, outroSkipSeconds = 0),
      ),
    )
  }

  @Test
  fun `direct file queue intro skip target resolves to chapter-relative position`() {
    assertEquals(
      12.0,
      resolveIntroSkipTargetChapterPosition(
        settings = BookSkipSettings(introSkipSeconds = 12, outroSkipSeconds = 0),
      ),
    )
  }

  @Test
  fun `direct file queue is identified when files and chapters are one to one`() {
    assertTrue(
      usesDirectFileQueue(
        detailedItem(
          files = listOf(bookFile("ch-1"), bookFile("ch-2")),
          chapters = listOf(chapter("ch-1"), chapter("ch-2")),
        ),
      ),
    )

    assertFalse(
      usesDirectFileQueue(
        detailedItem(
          files = listOf(bookFile("file-1")),
          chapters = listOf(chapter("ch-1"), chapter("ch-2")),
        ),
      ),
    )
  }

  @Test
  fun `direct file queue outro skip target resolves to next media item when available`() {
    assertEquals(
      DirectQueueOutroTarget.NextMediaItem(mediaItemIndex = 2),
      resolveDirectQueueOutroTarget(
        currentMediaItemIndex = 1,
        mediaItemCount = 3,
        currentChapterDurationSeconds = 95.0,
      ),
    )
  }

  @Test
  fun `direct file queue outro skip target resolves to final chapter boundary`() {
    assertEquals(
      DirectQueueOutroTarget.FinalBoundary(chapterPositionSeconds = 95.0),
      resolveDirectQueueOutroTarget(
        currentMediaItemIndex = 2,
        mediaItemCount = 3,
        currentChapterDurationSeconds = 95.0,
      ),
    )
  }

  @Test
  fun `outro skip target resolves to next chapter start when next chapter exists`() {
    assertEquals(
      120.0,
      resolveOutroSkipTargetTotalPosition(
        currentChapterIndex = 1,
        mediaItemCount = 3,
        chapterStartsSeconds = listOf(0.0, 60.0, 120.0),
        currentChapterDurationSeconds = 60.0,
      ),
    )
  }

  @Test
  fun `outro skip target resolves to current chapter end for final chapter`() {
    assertEquals(
      180.0,
      resolveOutroSkipTargetTotalPosition(
        currentChapterIndex = 2,
        mediaItemCount = 3,
        chapterStartsSeconds = listOf(0.0, 60.0, 120.0),
        currentChapterDurationSeconds = 60.0,
      ),
    )
  }

  @Test
  fun `same media item backward discontinuity resets applied skips`() {
    assertTrue(
      shouldResetAppliedSkipsAfterPositionDiscontinuity(
        oldMediaItemIndex = 1,
        newMediaItemIndex = 1,
        oldPositionMs = 30_000L,
        newPositionMs = 0L,
      ),
    )
  }

  @Test
  fun `same media item forward discontinuity does not reset applied skips`() {
    assertFalse(
      shouldResetAppliedSkipsAfterPositionDiscontinuity(
        oldMediaItemIndex = 1,
        newMediaItemIndex = 1,
        oldPositionMs = 0L,
        newPositionMs = 10_000L,
      ),
    )
  }

  @Test
  fun `media item transition discontinuity resets applied skips for new chapter visit`() {
    assertTrue(
      shouldResetAppliedSkipsAfterPositionDiscontinuity(
        oldMediaItemIndex = 1,
        newMediaItemIndex = 2,
        oldPositionMs = 95_000L,
        newPositionMs = 0L,
      ),
    )
  }

  @Test
  fun `automatic outro boundary completes only current chapter timer`() {
    assertTrue(shouldCompleteTimerAtAutomaticOutroBoundary(CurrentEpisodeTimerOption))
    assertFalse(shouldCompleteTimerAtAutomaticOutroBoundary(DurationTimerOption(duration = 30)))
    assertFalse(shouldCompleteTimerAtAutomaticOutroBoundary(null))
  }

  @Test
  fun `automatic outro boundary completes combined timer after countdown has already expired`() {
    assertTrue(
      shouldCompleteTimerAtAutomaticOutroBoundary(
        timerOption = DurationTimerOption(duration = 30, stopMode = DurationTimerStopMode.AFTER_CURRENT_EPISODE),
        stage = SleepTimerStage.WAITING_FOR_CURRENT_ITEM_END,
      ),
    )
  }

  @Test
  fun `automatic outro boundary does not complete combined timer before countdown expires`() {
    assertFalse(
      shouldCompleteTimerAtAutomaticOutroBoundary(
        timerOption = DurationTimerOption(duration = 30, stopMode = DurationTimerStopMode.AFTER_CURRENT_EPISODE),
        stage = SleepTimerStage.DURATION_COUNTDOWN,
      ),
    )
  }

  @Test
  fun `current chapter timer delay resolves from remaining chapter duration and playback speed`() {
    assertEquals(
      20.0,
      resolveCurrentChapterTimerDelaySeconds(
        chapterDurationSeconds = 100.0,
        chapterPositionSeconds = 60.0,
        playbackSpeed = 2.0f,
      ),
    )
  }

  @Test
  fun `current chapter timer delay is not resolved without known duration`() {
    assertEquals(
      null,
      resolveCurrentChapterTimerDelaySeconds(
        chapterDurationSeconds = 0.0,
        chapterPositionSeconds = 10.0,
        playbackSpeed = 1.0f,
      ),
    )
  }

  @Test
  fun `direct intro timer reschedule waits for duration without reapplying intro skip`() {
    val pending =
      createPendingDirectIntroTimerReschedule(
        currentMediaItemId = "chapter-1",
        targetChapterPositionSeconds = 12.0,
        chapterDurationSeconds = null,
      )

    assertEquals(
      PendingDirectIntroTimerReschedule(
        mediaItemId = "chapter-1",
        chapterPositionSeconds = 12.0,
      ),
      pending,
    )
    assertFalse(
      shouldRetryPendingDirectIntroTimerReschedule(
        pending = pending,
        currentMediaItemId = "chapter-1",
        chapterDurationSeconds = null,
      ),
    )
    assertTrue(
      shouldRetryPendingDirectIntroTimerReschedule(
        pending = pending,
        currentMediaItemId = "chapter-1",
        chapterDurationSeconds = 95.0,
      ),
    )
    assertFalse(
      shouldRetryPendingDirectIntroTimerReschedule(
        pending = pending,
        currentMediaItemId = "chapter-2",
        chapterDurationSeconds = 95.0,
      ),
    )
  }

  @Test
  fun `direct intro timer reschedule is not pending when duration is already known`() {
    assertNull(
      createPendingDirectIntroTimerReschedule(
        currentMediaItemId = "chapter-1",
        targetChapterPositionSeconds = 12.0,
        chapterDurationSeconds = 95.0,
      ),
    )
  }

  @Test
  fun `direct queue relative seek stays on current media item and current file position`() {
    assertEquals(
      DirectQueueSeekTarget(mediaItemIndex = 2, positionMs = 15_000L),
      resolveDirectQueueRelativeSeekTarget(
        currentMediaItemIndex = 2,
        mediaItemCount = 4,
        currentPositionMs = 45_000L,
        currentDurationMs = 0L,
        seekOffsetMs = -30_000L,
      ),
    )

    assertEquals(
      DirectQueueSeekTarget(mediaItemIndex = 2, positionMs = 0L),
      resolveDirectQueueRelativeSeekTarget(
        currentMediaItemIndex = 2,
        mediaItemCount = 4,
        currentPositionMs = 10_000L,
        currentDurationMs = 0L,
        seekOffsetMs = -30_000L,
      ),
    )

    assertEquals(
      DirectQueueSeekTarget(mediaItemIndex = 2, positionMs = 120_000L),
      resolveDirectQueueRelativeSeekTarget(
        currentMediaItemIndex = 2,
        mediaItemCount = 4,
        currentPositionMs = 110_000L,
        currentDurationMs = 120_000L,
        seekOffsetMs = 30_000L,
      ),
    )
  }

  @Test
  fun `direct queue total position seek maps only representable synthetic chapter positions`() {
    assertEquals(
      DirectQueueSeekTarget(mediaItemIndex = 1, positionMs = 500L),
      resolveDirectQueueTotalPositionSeekTarget(
        chapterStartsSeconds = listOf(0.0, 1.0, 2.0),
        chapterEndsSeconds = listOf(1.0, 2.0, 3.0),
        totalPositionSeconds = 1.5,
      ),
    )

    assertNull(
      resolveDirectQueueTotalPositionSeekTarget(
        chapterStartsSeconds = listOf(0.0, 0.0, 0.0),
        chapterEndsSeconds = listOf(0.0, 0.0, 0.0),
        totalPositionSeconds = 10.0,
      ),
    )
  }

  private fun detailedItem(
    files: List<BookFile>,
    chapters: List<PlayingChapter>,
  ) = DetailedItem(
    id = "book-1",
    title = "Book",
    subtitle = null,
    author = null,
    narrator = null,
    publisher = null,
    series = emptyList(),
    year = null,
    abstract = null,
    files = files,
    chapters = chapters,
    progress = null,
    libraryId = "webdav_library",
    localProvided = false,
    createdAt = 0L,
    updatedAt = 0L,
  )

  private fun bookFile(id: String) =
    BookFile(
      id = id,
      name = id,
      duration = 0.0,
      size = null,
      mimeType = "audio/mpeg",
    )

  private fun chapter(id: String) =
    PlayingChapter(
      available = true,
      podcastEpisodeState = null,
      duration = 0.0,
      start = 0.0,
      end = 1.0,
      title = id,
      id = id,
    )
}
