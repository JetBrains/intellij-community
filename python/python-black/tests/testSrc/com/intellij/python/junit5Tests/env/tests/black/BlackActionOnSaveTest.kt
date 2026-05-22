// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.tests.black

import com.intellij.codeInsight.actions.onSave.FormatOnSaveOptions
import com.intellij.ide.actionsOnSave.impl.ActionsOnSaveManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.python.junit5Tests.framework.env.PyEnvTestCase
import com.intellij.python.junit5Tests.framework.env.pySdkFixture
import com.intellij.python.pytools.getState
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.fixture.editorFixture
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.psiFileFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.python.black.BlackPyTool
import com.intellij.python.pytools.configuration.ExecutableDiscoveryMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

/**
 * Exercises Black formatting on document save. Migrated from JUnit4 `BlackActionOnSaveTest`.
 *
 * The legacy implementation hooked into `BlackFormatterActionOnSave` (an `ActionOnSave`
 * extension) that has been removed in favour of the generic
 * [com.intellij.codeInsight.actions.onSave.FormatOnSaveAction] dispatched through
 * [FormatOnSaveOptions]. This test asserts the same behaviour via that generic path by
 * invoking the `SaveDocument` action against an `editorFixture`-opened editor.
 */
@PyEnvTestCase
internal class BlackActionOnSaveTest {
  companion object {
    val tempPathFixture = tempPathFixture()
    val projectFixture = projectFixture(tempPathFixture, openAfterCreation = true)
    val moduleFixture = projectFixture.moduleFixture(tempPathFixture, addPathToSourceRoot = true)
    val sdkFixture = pySdkFixture().pyEnvSdkFixture(moduleFixture)

    @JvmStatic
    @BeforeAll
    fun enableBlack() {
      with (BlackPyTool.getInstance().getState(projectFixture.get())) {
        enabled = true
        discoveryMode = ExecutableDiscoveryMode.INTERPRETER
      }
    }
  }

  // Instance-scoped so the editor is closed before TestApplicationExtension's afterEach runs
  // its `checkEditorsReleased` assertion. A class-scoped editorFixture lives until class teardown,
  // which is too late and shows up as a leaked editor.
  private val sourceRootFixture = moduleFixture.sourceRootFixture()
  private val psiFileFixture = sourceRootFixture.psiFileFixture("test.py", "print('abc')\n")
  private val editorFixture = psiFileFixture.editorFixture()

  private val project get() = projectFixture.get()

  @Test
  fun testActionOnSave() {
    val originalRunOnSave = FormatOnSaveOptions.getInstance(project).isRunOnSaveEnabled
    FormatOnSaveOptions.getInstance(project).isRunOnSaveEnabled = true
    try {
      val editor = editorFixture.get()
      timeoutRunBlocking {
        withContext(Dispatchers.EDT) {
          writeIntentReadAction {
            // Dirty the document so the save has something to flush.
            WriteCommandAction.runWriteCommandAction(project) {
              editor.document.insertString(editor.document.textLength, "\n")
            }
          }
          // The 'Run actions on save' gate (ActionsOnSaveManager.runningSaveDocumentAction) only flips
          // when the platform SaveDocumentAction goes through the action system, so we drive it through
          // ActionManager rather than calling FileDocumentManager.saveDocument directly.
          //
          // Building the DataContext explicitly (instead of `EditorUtil.getEditorDataContext`) — the
          // editor's content-component DataContext is populated by Swing data providers that aren't
          // wired up in this headless test app, so `CommonDataKeys.EDITOR` ends up null and
          // SaveDocumentAction silently no-ops.
          val action = ActionManager.getInstance().getAction("SaveDocument")
          val dataContext = SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, project)
            .add(CommonDataKeys.EDITOR, editor)
            .add(CommonDataKeys.VIRTUAL_FILE, editor.virtualFile)
            .build()
          val event = AnActionEvent.createEvent(action, dataContext, null, ActionPlaces.UNKNOWN, ActionUiKind.NONE, null)
          ActionUtil.performAction(action, event)
        }
        ActionsOnSaveManager.getInstance(project).awaitPendingActions()
      }
      val actual = runReadAction { editor.document.text }
      assertEquals("print(\"abc\")\n", actual)
    }
    finally {
      FormatOnSaveOptions.getInstance(project).isRunOnSaveEnabled = originalRunOnSave
    }
  }
}
