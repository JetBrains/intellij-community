package com.intellij.ide.starter.testContextCache.utils

import com.intellij.openapi.util.io.NioFiles
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeText

@OptIn(ExperimentalPathApi::class)
class FileTreeSnapshotTest {
  @TempDir
  lateinit var root: Path

  @Test
  fun `capture skips a directory that disappears during the walk`() {
    createDirectoryWithFile("a")
    createDirectoryWithFile("b")

    FileTreeSnapshot.capture(root, deleteOtherDirectoryOnFirstVisit()).use { snapshot ->
      assertEquals(1, snapshot.trackedFilesCount)
      assertEquals(1, snapshot.trackedDirectoriesCount)
    }
  }

  @Test
  fun `changes report a directory that disappears during the walk as deleted`() {
    createDirectoryWithFile("a")
    createDirectoryWithFile("b")
    var armed = false
    val deleteOnFirstVisit = deleteOtherDirectoryOnFirstVisit()

    FileTreeSnapshot.capture(root, shouldTrackDirectory = { relativePath -> !armed || deleteOnFirstVisit(relativePath) }).use { snapshot ->
      armed = true
      val changes = snapshot.changes()

      assertEquals(1, changes.deletedFiles.size)
      assertEquals(1, changes.deletedDirectories.size)
      assertEquals(changes.deletedFiles.single().parent, changes.deletedDirectories.single())
    }
  }

  @Test
  fun `a snapshot with a base copies only the files that changed since the base`() {
    createDirectoryWithFile("unchanged")
    createDirectoryWithFile("changed")

    FileTreeSnapshot.capture(root, shouldTrackDirectory = { true }).use { base ->
      root.resolve("changed/file.txt").writeText("opened")
      FileTreeSnapshot.capture(root, shouldTrackDirectory = { true }, base = base).use { snapshot ->
        assertEquals(2, snapshot.trackedFilesCount)
        assertEquals(1, snapshot.sharedFilesCount)
        assertFalse(snapshot.snapshotDir.resolve("unchanged/file.txt").exists())
        assertTrue(snapshot.snapshotDir.resolve("changed/file.txt").exists())
      }
    }
  }

  @Test
  fun `a snapshot with a base restores the files that it shares with the base`() {
    createDirectoryWithFile("unchanged")
    createDirectoryWithFile("changed")
    root.resolve("empty").createDirectories()

    FileTreeSnapshot.capture(root, shouldTrackDirectory = { true }).use { base ->
      root.resolve("changed/file.txt").writeText("opened")
      FileTreeSnapshot.capture(root, shouldTrackDirectory = { true }, base = base).use { snapshot ->
        root.resolve("unchanged/file.txt").writeText("left by a test")
        root.resolve("changed/file.txt").writeText("left by a test")
        snapshot.restoreTracked(root, snapshot.changes())
        assertEquals("unchanged", root.resolve("unchanged/file.txt").readText())
        assertEquals("opened", root.resolve("changed/file.txt").readText())

        root.resolve("unchanged").deleteRecursively()
        snapshot.restoreAll()
        assertEquals("unchanged", root.resolve("unchanged/file.txt").readText())
        assertEquals("opened", root.resolve("changed/file.txt").readText())
        assertTrue(root.resolve("empty").isDirectory())
        assertFalse(snapshot.hasChanges())
      }
    }
  }

  @Test
  fun `a restore deletes and replaces read-only files`() {
    createDirectoryWithFile("tracked")
    NioFiles.setReadOnly(root.resolve("tracked/file.txt"), true)

    val snapshotDir = FileTreeSnapshot.capture(root, shouldTrackDirectory = { true }).use { snapshot ->
      NioFiles.setReadOnly(root.resolve("tracked/file.txt"), false)
      root.resolve("tracked/file.txt").writeText("left by a test")
      NioFiles.setReadOnly(root.resolve("tracked/file.txt"), true)
      createDirectoryWithFile("new")
      NioFiles.setReadOnly(root.resolve("new/file.txt"), true)
      root.resolve("tracked/new.txt").writeText("new")
      NioFiles.setReadOnly(root.resolve("tracked/new.txt"), true)

      snapshot.restoreTracked(root, snapshot.changes())

      assertEquals("tracked", root.resolve("tracked/file.txt").readText())
      assertFalse(root.resolve("new").exists())
      assertFalse(root.resolve("tracked/new.txt").exists())
      assertFalse(snapshot.hasChanges())
      snapshot.snapshotDir
    }
    assertFalse(snapshotDir.exists())
  }

  private fun createDirectoryWithFile(name: String) {
    root.resolve(name).resolve("file.txt").apply {
      parent.createDirectories()
      writeText(name)
    }
  }

  /**
   * Deletes the sibling of the first visited directory. The walk has listed the sibling already, so it fails to read
   * the sibling attributes.
   */
  private fun deleteOtherDirectoryOnFirstVisit(): (Path) -> Boolean {
    var deleted = false
    return { relativePath ->
      if (!deleted) {
        deleted = true
        root.resolve(if (relativePath == Path.of("a")) "b" else "a").deleteRecursively()
      }
      true
    }
  }
}
