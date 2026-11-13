// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit

import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.testFramework.junit5.RegistryKeyAppLevel
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.workspaceModel.ide.registerProjectRoot
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * The Python plugin excludes each `.tox` and `.nox` directory below the project root and below a content root of a module of any type.
 * The test turns off the default lines of `.analysisignore`, so that only the rule of the Python plugin excludes the directories.
 */
@TestApplication
@RegistryKeyAppLevel(key = "ide.analysisignore.defaults.enabled", value = "false")
class PyTestEnvExclusionTest {
  private val tempDir = tempPathFixture()
  private val project = projectFixture(openAfterCreation = true)
  private val module = project.moduleFixture(tempDir, addPathToSourceRoot = true)

  @Test
  fun `tox and nox directories in a content root are excluded`(): Unit = runBlocking {
    module.get()
    assertTestEnvExcluded(project.get(), tempDir.get())
  }

  @Test
  fun `tox and nox directories in the project root are excluded`(): Unit = runBlocking {
    module.get()
    registerProjectRoot(project.get(), tempDir.get())
    assertTestEnvExcluded(project.get(), tempDir.get())
  }

  private suspend fun assertTestEnvExcluded(project: Project, root: Path) {
    for (name in listOf(".tox", ".nox")) {
      val envFile = createFile(root, "$name/py312/lib/a.py")
      val nestedEnvFile = createFile(root, "pkg/$name/b.py")
      val similarName = createFile(root, "${name}ic/c.py")

      assertEquals(false, isInContent(project, envFile), "a file in the $name directory")
      assertEquals(false, isInContent(project, nestedEnvFile), "a file in a nested $name directory")
      assertEquals(true, isInContent(project, similarName), "a file in ${name}ic")
    }
    assertEquals(true, isInContent(project, createFile(root, "main.py")), "a sibling of the excluded directories")
  }

  private fun createFile(root: Path, relativePath: String): VirtualFile {
    val path = root.resolve(relativePath)
    Files.createDirectories(path.parent)
    Files.createFile(path)
    return checkNotNull(VirtualFileManager.getInstance().refreshAndFindFileByNioPath(path)) { "no virtual file for $path" }
  }

  private suspend fun isInContent(project: Project, file: VirtualFile): Boolean = readAction {
    ProjectFileIndex.getInstance(project).isInContent(file)
  }
}
