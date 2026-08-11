// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package org.intellij.plugins.markdown.actions

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.vcs.readOnlyHandler.ReadonlyStatusHandlerImpl
import com.intellij.openapi.vfs.ReadonlyStatusHandler
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import org.intellij.plugins.markdown.ui.actions.styling.CreateOrChangeListActionGroup
import org.intellij.plugins.markdown.ui.actions.styling.SetHeaderLevelImpl

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
class MarkdownStylingActionsReadOnlyDocumentTest : LightPlatformCodeInsightTestCase() {

  // User declines the "make writable" prompt — file stays read-only, action must no-op.

  fun `test unordered list is a no-op when user declines`() = doNoOpTest(CreateOrChangeListActionGroup.UnorderedList())
  fun `test bold is a no-op when user declines`() = doNoOpTestById("org.intellij.plugins.markdown.ui.actions.styling.ToggleBoldAction")
  fun `test header title is a no-op when user declines`() = doNoOpTest(SetHeaderLevelImpl.Title())

  private fun doNoOpTestById(actionId: String) {
    val action = ActionManager.getInstance().getAction(actionId)
    assertNotNull("action $actionId is not registered", action)
    doNoOpTest(action)
  }

  private fun doNoOpTest(action: AnAction) {
    // language=Markdown
    val content = """
      <selection>Some arbitrary text
      Some other line</selection>
    """.trimIndent()
    configureFromFileText("some.md", content)
    WriteAction.runAndWait<RuntimeException> { vFile.isWritable = false }
    EditorTestUtil.executeAction(editor, false, action)
    assertFalse("file must remain read-only after action bails out", vFile.isWritable)
    checkResultByText(content)
  }

  // User accepts the "make writable" prompt — file is cleared, action proceeds.

  fun `test unordered list proceeds when user accepts`() {
    val content = "<selection>Some arbitrary text</selection>"
    val applied = "* Some arbitrary text"
    configureFromFileText("some.md", content)
    val handler = ReadonlyStatusHandler.getInstance(project) as ReadonlyStatusHandlerImpl
    handler.setClearReadOnlyInTests(true)
    try {
      WriteAction.runAndWait<RuntimeException> { vFile.isWritable = false }
      EditorTestUtil.executeAction(editor, false, CreateOrChangeListActionGroup.UnorderedList())
      assertTrue("file should have been cleared read-only", vFile.isWritable)
      checkResultByText(applied)
    }
    finally {
      handler.setClearReadOnlyInTests(false)
    }
  }
}
