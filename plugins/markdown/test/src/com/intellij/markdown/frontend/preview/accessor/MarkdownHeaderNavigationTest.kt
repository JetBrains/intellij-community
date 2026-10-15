// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.frontend.preview.accessor

import com.intellij.ide.vfs.rpcId
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.psiFileFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.plugins.markdown.dto.MarkdownHeaderInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@TestApplication
internal class MarkdownHeaderNavigationTest {
  private val projectFixture = projectFixture()
  private val sourceRoot = projectFixture.moduleFixture("src").sourceRootFixture()
  private val targetFile = sourceRoot.psiFileFixture("target.md", "# First\n\n## Target\n")

  @Test
  fun `header navigation accepts a Windows path`() {
    checkHeaderNavigation("C:/project/target.md")
  }

  @Test
  fun `header navigation accepts a Windows path with Cyrillic and spaces`() {
    checkHeaderNavigation("C:/проект с пробелами/target.md")
  }

  @Test
  fun `header navigation accepts a Unix path`() {
    checkHeaderNavigation("/project/target.md")
  }

  private fun checkHeaderNavigation(filePath: String): Unit = timeoutRunBlocking {
    val project = projectFixture.get()
    val file = targetFile.get().virtualFile
    val targetOffset = 9
    val header = MarkdownHeaderInfo("Target", filePath, file.name, 2, targetOffset, file.rpcId())

    withContext(Dispatchers.EDT) {
      val manager = FileEditorManager.getInstance(project)
      file.putUserData(FileEditorProvider.KEY, TextEditorProvider.getInstance())
      try {
        runReadAction {
          MarkdownLinkOpenerUtil.navigateToHeader(project, header)
        }
        assertEquals(listOf(file), manager.selectedFiles.toList())
        assertEquals(targetOffset, manager.selectedTextEditor?.caretModel?.offset)
      }
      finally {
        manager.closeFile(file)
        file.putUserData(FileEditorProvider.KEY, null)
      }
    }
  }
}
