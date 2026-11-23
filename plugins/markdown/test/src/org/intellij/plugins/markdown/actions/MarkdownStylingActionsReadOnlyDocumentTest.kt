// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package org.intellij.plugins.markdown.actions

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.vcs.readOnlyHandler.ReadonlyStatusHandlerImpl
import com.intellij.openapi.vfs.ReadonlyStatusHandler
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.plugins.markdown.ui.actions.styling.CreateOrChangeListActionGroup
import org.intellij.plugins.markdown.ui.actions.styling.SetHeaderLevelImpl
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Markdown styling actions on a read-only file.
 *
 * All styling actions route their writes through
 * [com.intellij.openapi.command.WriteCommandAction.writeCommandAction] with the PSI file,
 * which delegates to [com.intellij.codeInsight.FileModificationService.preparePsiElementsForWrite]
 * → [ReadonlyStatusHandler.ensureFilesWritable] before entering the write action.
 *
 * In production this shows the "make writable" dialog. In unit-test mode the dialog is
 * replaced by [ReadonlyStatusHandlerImpl.setClearReadOnlyInTests] which decides whether
 * `ensureFilesWritable` behaves as if the user accepted or declined:
 *
 *  * `false` (default): user declined → file stays read-only, action must silently no-op.
 *  * `true`: user accepted → file is cleared, action proceeds and mutates the document.
 */
@TestApplication
class MarkdownStylingActionsReadOnlyDocumentTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  // User declines the "make writable" prompt — file stays read-only, action must no-op.

  @Test
  fun `test unordered list is a no-op when user declines`(): Unit = doNoOpTest(CreateOrChangeListActionGroup.UnorderedList())
  @Test
  fun `test header title is a no-op when user declines`(): Unit = doNoOpTest(SetHeaderLevelImpl.Title())
  @Test
  fun `test bold is a no-op when user declines`() {
    val actionId = MarkdownStylingActionIds.TOGGLE_BOLD
    val action = ActionManager.getInstance().getAction(actionId)
    assertNotNull(action, "action $actionId is not registered")
    doNoOpTest(action)
  }

  private fun doNoOpTest(action: AnAction): Unit = timeoutRunBlocking {
    // language=Markdown
    val content = """
      <selection>Some arbitrary text
      Some other line</selection>
    """.trimIndent()
    myFixture.configureByText("some.md", content)
    val vFile = myFixture.file.virtualFile
    WriteAction.runAndWait<RuntimeException> { vFile.isWritable = false }
    withContext(Dispatchers.EDT) {
      EditorTestUtil.executeAction(myFixture.editor, false, action)
      assertFalse(vFile.isWritable, "file must remain read-only after action bails out")
      myFixture.checkResult(content)
    }
  }

  // User accepts the "make writable" prompt — file is cleared, action proceeds.

  @Test
  fun `test unordered list proceeds when user accepts`(): Unit = timeoutRunBlocking {
    val content = "<selection>Some arbitrary text</selection>"
    val applied = "* Some arbitrary text"
    myFixture.configureByText("some.md", content)
    val vFile = myFixture.file.virtualFile
    val handler = ReadonlyStatusHandler.getInstance(myFixture.project) as ReadonlyStatusHandlerImpl
    handler.setClearReadOnlyInTests(true)
    try {
      WriteAction.runAndWait<RuntimeException> { vFile.isWritable = false }
      withContext(Dispatchers.EDT) {
        EditorTestUtil.executeAction(myFixture.editor, false, CreateOrChangeListActionGroup.UnorderedList())
        assertTrue(vFile.isWritable, "file should have been cleared read-only")
        myFixture.checkResult(applied)
      }
    }
    finally {
      handler.setClearReadOnlyInTests(false)
    }
  }
}
