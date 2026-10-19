package com.intellij.ide.starter.testContextCache.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class ProjectFilesRestorerExclusionsTest {
  @TempDir
  lateinit var projectDir: Path

  @Test
  fun `new files under default excluded directories are ignored`() {
    ProjectFilesRestorer(projectDir).use { restorer ->
      restorer.captureSnapshot()

      createFile("build/generated.txt", "generated")

      assertFalse(restorer.hasChanges())
    }
  }

  @Test
  fun `the build roots of a nested build are ignored`() {
    createFile("app/build.gradle.kts", "")
    createFile("app/buildSrc/build/classes/A.class", "baseline")
    createFile("lib/pom.xml", "<project />")

    ProjectFilesRestorer(projectDir).use { restorer ->
      restorer.captureSnapshot()

      createFile("app/.gradle/8.14/fileHashes.lock", "lock")
      createFile("app/build/classes/Main.class", "compiled")
      createFile("app/buildSrc/build/classes/A.class", "changed")
      createFile("lib/target/test-classes/Test.class", "compiled")

      assertFalse(restorer.hasChanges())
    }
  }

  @Test
  fun `a nested build directory with no build script is tracked`() {
    ProjectFilesRestorer(projectDir).use { restorer ->
      restorer.captureSnapshot()

      createFile("src/main/java/com/example/build/Tool.java", "class Tool {}")

      assertTrue(restorer.hasChanges())
    }
  }

  @Test
  fun `project metadata outside default exclusions is tracked`() {
    ProjectFilesRestorer(projectDir).use { restorer ->
      restorer.captureSnapshot()

      createFile(".idea/workspace.xml", "<workspace />")

      assertTrue(restorer.hasChanges())
    }
  }

  /** The restore to the prepared state, see [com.intellij.ide.starter.testContextCache.CachedTestContext.restorePreparedProjectFiles]. */
  @Test
  fun `a restorer with no exclusions rewinds the build roots`() {
    createFile("target/classes/Main.class", "baseline")

    ProjectFilesRestorer(projectDir, excludedDirectories = emptySet()).use { restorer ->
      restorer.captureSnapshot()

      createFile("target/classes/Main.class", "changed")
      createFile("build/generated.txt", "generated")
      assertTrue(restorer.hasChanges())

      restorer.restore()
      assertFalse(restorer.hasChanges())
      assertEquals("baseline", projectDir.resolve("target/classes/Main.class").readText())
      assertFalse(projectDir.resolve("build/generated.txt").exists())
    }
  }

  @Test
  fun `default exclusions cover build managed roots but not project metadata`() {
    assertTrue("build" in ProjectFilesRestorer.DEFAULT_EXCLUDED_DIRECTORIES)
    assertTrue("target" in ProjectFilesRestorer.DEFAULT_EXCLUDED_DIRECTORIES)
    assertFalse(".idea" in ProjectFilesRestorer.DEFAULT_EXCLUDED_DIRECTORIES)
  }

  private fun createFile(relativePath: String, content: String) {
    val file = projectDir.resolve(relativePath)
    file.parent?.createDirectories()
    file.writeText(content)
  }
}
