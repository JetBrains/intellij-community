// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit

import com.intellij.ide.analysisignore.AnalysisIgnoreService
import com.intellij.openapi.application.readAction
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.workspaceModel.ide.registerProjectRoot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Files

/**
 * PyCharm has no rule of its own for `.tox`. The default lines of `.analysisignore` exclude it in a project root.
 */
@TestApplication
class PyToxExclusionTest {
  private val tempDir = tempPathFixture()
  private val project = projectFixture(openAfterCreation = true)
  private val module = project.moduleFixture(tempDir, addPathToSourceRoot = true)

  @Test
  fun `a tox directory in the project root and in a nested package is excluded`(): Unit = runBlocking {
    setUpProjectRoot()
    val toxFile = createFile(".tox/py312/lib/a.py")
    val nestedToxFile = createFile("pkg/.tox/b.py")
    val sibling = createFile("main.py")
    val similarName = createFile(".toxic/c.py")

    assertEquals(false, isInContent(toxFile), "a file in the .tox directory")
    assertEquals(false, isInContent(nestedToxFile), "a file in a nested .tox directory")
    assertEquals(true, isInContent(sibling), "a sibling of the .tox directory")
    assertEquals(true, isInContent(similarName), "a file in .toxic")
  }

  private suspend fun setUpProjectRoot() {
    module.get()
    val project = project.get()
    registerProjectRoot(project, tempDir.get())
    withContext(Dispatchers.Default) { AnalysisIgnoreService.getInstance(project).syncDefaultsBlocking() }
  }

  private fun createFile(relativePath: String): VirtualFile {
    val path = tempDir.get().resolve(relativePath)
    Files.createDirectories(path.parent)
    Files.createFile(path)
    return checkNotNull(VirtualFileManager.getInstance().refreshAndFindFileByNioPath(path)) { "no virtual file for $path" }
  }

  private suspend fun isInContent(file: VirtualFile): Boolean = readAction {
    ProjectFileIndex.getInstance(project.get()).isInContent(file)
  }
}
