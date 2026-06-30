package io.github.hobin66.webdavplayer.content.cache.persistent

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalCacheMigrationTest {
  @get:Rule
  val helper =
    MigrationTestHelper(
      InstrumentationRegistry.getInstrumentation(),
      LocalCacheStorage::class.java,
    )

  @Test
  fun migration1To2_preservesBookmarksAndAddsChapterColumns() {
    helper
      .createDatabase(TEST_DATABASE, 1)
      .apply {
        execSQL(
          """
          INSERT INTO cached_bookmark(
            id,
            title,
            libraryItemId,
            createdAt,
            totalPosition,
            syncState
          ) VALUES(
            'bookmark-1',
            'Chapter 1 - 00:10',
            'book-1',
            1000,
            10,
            0
          )
          """.trimIndent(),
        )
        close()
      }

    val db =
      helper.runMigrationsAndValidate(
        TEST_DATABASE,
        2,
        true,
        LocalCacheStorage.MIGRATION_1_2,
      )

    db
      .query("SELECT title, chapterId, chapterPosition FROM cached_bookmark WHERE id = 'bookmark-1'")
      .use { cursor ->
        assertTrue(cursor.moveToFirst())
        assertEquals("Chapter 1 - 00:10", cursor.getString(0))
        assertTrue(cursor.isNull(1))
        assertTrue(cursor.isNull(2))
      }
    db.close()
  }

  companion object {
    private const val TEST_DATABASE = "migration-test"
  }
}
