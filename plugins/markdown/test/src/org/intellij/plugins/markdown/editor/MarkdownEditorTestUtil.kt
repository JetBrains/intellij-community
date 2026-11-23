// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("MarkdownEditorTestUtil")
package org.intellij.plugins.markdown.editor

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.actionSystem.TypedAction
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.awaitPendingNavigationAndEditorOpenIfEnabled
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.TestOnly

/**
 * Types [text] into the editor of the fixture, one char at a time, as `LightPlatformCodeInsightTestCase.type` does.
 *
 * A `'\n'` runs [IdeActions.ACTION_EDITOR_ENTER] with [executeAction].
 * Each other char goes to [TypedAction] with the default data context.
 * [CodeInsightTestFixture.type] is different. It tries the keymap shortcut of the char first, and it fires the typing events.
 */
@RequiresEdt
@TestOnly
fun CodeInsightTestFixture.typeInEditor(text: String) {
  for (c in text) {
    if (c == '\n') {
      executeAction(IdeActions.ACTION_EDITOR_ENTER)
    }
    else {
      TypedAction.getInstance().actionPerformed(editor, c, DataManager.getInstance().dataContext)
    }
  }
}

/**
 * Types one char, as [typeInEditor] for a string does.
 */
@RequiresEdt
@TestOnly
fun CodeInsightTestFixture.typeInEditor(c: Char) {
  typeInEditor(c.toString())
}

/**
 * Runs the editor action [actionId] in a command, as `LightPlatformCodeInsightTestCase.executeAction` does.
 *
 * The test fails when the action is disabled.
 * Then the function waits for the pending navigation and the editor opening.
 * [CodeInsightTestFixture.performEditorAction] is different. It runs no command, and it skips a disabled action.
 */
@RequiresEdt
@TestOnly
fun CodeInsightTestFixture.executeAction(actionId: String) {
  val editor = editor
  CommandProcessor.getInstance().executeCommand(project, { EditorTestUtil.executeAction(editor, actionId, true) }, "", null, editor.document)
  awaitPendingNavigationAndEditorOpenIfEnabled(project)
}

/**
 * Runs [IdeActions.ACTION_EDITOR_BACKSPACE], as `LightPlatformCodeInsightTestCase.backspace` does.
 */
@RequiresEdt
@TestOnly
fun CodeInsightTestFixture.backspace() {
  executeAction(IdeActions.ACTION_EDITOR_BACKSPACE)
}

/**
 * Runs [IdeActions.ACTION_EDITOR_DELETE], as `LightPlatformCodeInsightTestCase.delete` does.
 */
@RequiresEdt
@TestOnly
fun CodeInsightTestFixture.delete() {
  executeAction(IdeActions.ACTION_EDITOR_DELETE)
}
