package com.intellij.ide.starter.testContextCache.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class ProjectFilesRestorerTest {
  @Test
  fun `filesystem restore tracks idea metadata by default`(@TempDir tempDir: Path) {
    val projectDir = tempDir.resolve("project").apply { createDirectories() }
    val sourceFile = projectDir.resolve("src/Main.kt").apply {
      parent.createDirectories()
      writeText("class Main")
    }
    val workspaceFile = projectDir.resolve(".idea/workspace.xml").apply {
      parent.createDirectories()
      writeText("<workspace />")
    }
    val buildFile = projectDir.resolve("build/generated.txt").apply {
      parent.createDirectories()
      writeText("baseline-build")
    }
    val gradleWrapperFile = projectDir.resolve("gradle/wrapper/gradle-wrapper.properties").apply {
      parent.createDirectories()
      writeText("distributionUrl=baseline")
    }
    val newTrackedFile = projectDir.resolve("notes.txt")
    val newExcludedFile = projectDir.resolve("build/new.txt")

    ProjectFilesRestorer(projectDir).use { restorer ->
      restorer.captureSnapshot()

      sourceFile.writeText("class Changed")
      workspaceFile.writeText("<workspace changed=\"true\" />")
      buildFile.writeText("changed-build")
      gradleWrapperFile.writeText("distributionUrl=changed")
      newTrackedFile.writeText("temporary")
      newExcludedFile.writeText("keep")

      assertTrue(restorer.hasChanges())

      assertTrue(restorer.restore())

      assertEquals("class Main", sourceFile.readText())
      assertEquals("<workspace />", workspaceFile.readText())
      assertEquals("changed-build", buildFile.readText())
      assertEquals("distributionUrl=baseline", gradleWrapperFile.readText())
      assertFalse(newTrackedFile.exists())
      assertTrue(newExcludedFile.exists())
    }
  }

  @TempDir
  lateinit var projectPath: Path

  @Test
  fun restoreRemovesNewEmptyDirectories() {
    ProjectFilesRestorer(projectPath).use { restorer ->
      restorer.captureSnapshot()

      val newDirectory = projectPath.resolve("src/newPackage")
      Files.createDirectories(newDirectory)

      assertTrue(restorer.hasChanges())

      restorer.restore()

      assertFalse(Files.exists(newDirectory))
      assertFalse(restorer.hasChanges())
    }
  }

  @Test
  fun restoreRemovesDirectoryCreatedForNewFile() {
    ProjectFilesRestorer(projectPath).use { restorer ->
      restorer.captureSnapshot()

      val newFile = projectPath.resolve("src/newPackage/NewClass.kt")
      Files.createDirectories(newFile.parent)
      Files.writeString(newFile, "class NewClass")

      assertTrue(restorer.hasChanges())

      assertTrue(restorer.restore())

      assertFalse(Files.exists(newFile))
      assertFalse(Files.exists(newFile.parent))
      assertFalse(restorer.hasChanges())
    }
  }

  @Test
  fun restoreRecreatesDeletedEmptySnapshotDirectories() {
    val existingDirectory = projectPath.resolve("src/emptyPackage")
    Files.createDirectories(existingDirectory)

    ProjectFilesRestorer(projectPath).use { restorer ->
      restorer.captureSnapshot()

      Files.delete(existingDirectory)

      assertTrue(restorer.hasChanges())

      restorer.restore()

      assertTrue(Files.isDirectory(existingDirectory))
      assertFalse(restorer.hasChanges())
    }
  }

  @Test
  fun restoreIgnoresTopLevelExcludedDirectories() {
    ProjectFilesRestorer(projectPath).use { restorer ->
      restorer.captureSnapshot()

      val generatedFile = projectPath.resolve("build/generated.txt")
      Files.createDirectories(generatedFile.parent)
      Files.writeString(generatedFile, "generated")

      assertFalse(restorer.hasChanges())

      assertFalse(restorer.restore())

      assertTrue(Files.exists(generatedFile))
    }
  }

  @Test
  fun restoreTracksNestedDirectoriesNamedLikeExcludedRoots() {
    val nestedBuildFile = projectPath.resolve("src/build/Existing.kt")
    Files.createDirectories(nestedBuildFile.parent)
    Files.writeString(nestedBuildFile, "class Existing")

    val nestedTargetDirectory = projectPath.resolve("docs/target/empty")
    Files.createDirectories(nestedTargetDirectory)

    ProjectFilesRestorer(projectPath).use { restorer ->
      restorer.captureSnapshot()

      val newNestedTargetFile = projectPath.resolve("docs/target/new.txt")
      Files.writeString(nestedBuildFile, "class Changed")
      Files.writeString(newNestedTargetFile, "temporary")
      Files.delete(nestedTargetDirectory)

      assertTrue(restorer.hasChanges())

      assertTrue(restorer.restore())

      assertEquals("class Existing", Files.readString(nestedBuildFile))
      assertFalse(Files.exists(newNestedTargetFile))
      assertTrue(Files.isDirectory(nestedTargetDirectory))
      assertFalse(restorer.hasChanges())
    }
  }
}
