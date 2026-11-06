// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.emmet

import com.intellij.codeInsight.template.CustomTemplateCallback
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.codeInsight.template.emmet.filters.ZenCodingFilter
import com.intellij.codeInsight.template.emmet.tokens.TemplateToken
import com.intellij.codeInsight.template.impl.TemplateImpl
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.codeInsight.template.impl.TemplateSettings
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightProjectFixture
import com.intellij.psi.PsiFile
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.awaitPendingNavigationAndEditorOpenIfEnabled
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.Assertions.assertEquals

/**
 * The helpers of an Emmet test: they expand an abbreviation, wrap a selection, update a tag, and apply a filter.
 * The test file of each helper has the name `test.<extension>`.
 * Create it with [emmetAbbreviationFixture].
 *
 * Call a member from a background thread. A member takes the read actions and the EDT calls that it needs.
 */
@TestOnly
class EmmetAbbreviationTestFixture(
  /**
   * The code insight fixture of the test, on a new project for each test.
   */
  val fixture: CodeInsightTestFixture,
  /**
   * The extension of the test file, for example `html`.
   */
  val extension: String,
) {
  val project: Project get() = fixture.project

  val editor: Editor get() = fixture.editor

  val file: PsiFile get() = fixture.file

  /**
   * Configures [text] in a file with [extension] and expands the abbreviation at the caret.
   * When [text] has no caret marker, the caret goes to the end of the line.
   */
  fun expand(text: String) {
    expand(text, extension)
  }

  /**
   * Configures [text] in a file with the [extension] of this call and expands the abbreviation at the caret.
   */
  fun expand(text: String, extension: String) {
    fixture.configureByText("test.$extension", text)
    runInEdtAndWait {
      if (!text.contains(EditorTestUtil.CARET_TAG)) {
        executeAction(IdeActions.ACTION_EDITOR_MOVE_LINE_END)
      }
      ApplicationManager.getApplication().runWriteAction {
        TemplateManager.getInstance(project).startTemplate(editor, TemplateSettings.TAB_CHAR)
      }
      waitForAsyncTasks()
    }
  }

  /**
   * Expands [sourceData] and compares the result with [expectedData], with the caret and the selection markers.
   */
  fun expandAndCheck(sourceData: String, expectedData: String) {
    expand(sourceData)
    fixture.checkResult(expectedData)
  }

  /**
   * Selects [selectedText] and starts [template] on the selection.
   */
  fun templateWrap(selectedText: String, template: TemplateImpl, expectedResult: String) {
    fixture.configureByText("text.$extension", selectSourceData(selectedText))
    runInEdtAndWait {
      ApplicationManager.getApplication().runWriteAction {
        TemplateManager.getInstance(project).startTemplate(editor, selectedText, template)
      }
      waitForAsyncTasks()
    }
    fixture.checkResult(expectedResult)
  }

  /**
   * Wraps the selection of [sourceData] with [emmetExpression].
   * When [sourceData] has no selection marker, the whole text is the selection.
   */
  fun emmetWrap(sourceData: String, emmetExpression: String, expectedData: String) {
    fixture.configureByText("test.$extension", selectSourceData(sourceData))
    runInEdtAndWait {
      val callback = CustomTemplateCallback(editor, file)
      ZenCodingTemplate.doWrap(emmetExpression, callback)
      while (true) {
        val state = TemplateManagerImpl.getTemplateState(editor) ?: break
        state.nextTab()
      }
      waitForAsyncTasks()
    }
    fixture.checkResult(expectedData)
  }

  /**
   * Updates the tag at the caret of [sourceData] with [emmetExpression].
   */
  fun emmetUpdateTag(sourceData: String, emmetExpression: String, expectedData: String) {
    fixture.configureByText("test.$extension", sourceData)
    EmmetAbbreviationBalloon.setTestingAbbreviation(emmetExpression, fixture.testRootDisposable)
    runInEdtAndWait {
      executeAction(IdeActions.ACTION_UPDATE_TAG_WITH_EMMET)
      waitForAsyncTasks()
    }
    fixture.checkResult(expectedData)
  }

  /**
   * Applies [filter] to the `div` template token of [sourceData] and compares the result with [expectedData].
   */
  fun doFilterText(sourceData: String, expectedData: String, filter: ZenCodingFilter) {
    fixture.configureByText("test.$extension", sourceData)
    val filteredData = runReadActionBlocking {
      val attribute2value = HashMap<String, String?>()
      val templateToken = TemplateToken("div", attribute2value)
      templateToken.setTemplateText(sourceData, file)
      val tag = templateToken.xmlTag
      if (tag != null) {
        for (attribute in tag.attributes) {
          attribute2value[attribute.name] = attribute.value
        }
      }
      filter.filterText(sourceData, templateToken)
    }
    assertEquals(expectedData, filteredData)
  }

  /**
   * Runs the editor action [actionId] in a command, as `LightPlatformCodeInsightTestCase.executeAction` does.
   * Call it on the EDT.
   */
  private fun executeAction(actionId: String) {
    CommandProcessor.getInstance().executeCommand(project, { EditorTestUtil.executeAction(editor, actionId, true) }, "", null,
                                                  editor.document)
    awaitPendingNavigationAndEditorOpenIfEnabled(project)
  }

  private fun waitForAsyncTasks() {
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
  }

  companion object {
    /**
     * Creates an [EmmetAbbreviationTestFixture] for test files with [extension], on a new project for each test.
     * It enables the template testing mode for the time of the test.
     * Declare the result as an instance field.
     */
    @JvmStatic
    fun emmetAbbreviationFixture(extension: String): TestFixture<EmmetAbbreviationTestFixture> =
      testFixture("emmetAbbreviationFixture") {
        val fixture = codeInsightFixture(codeInsightProjectFixture()).init()
        TemplateManagerImpl.setTemplateTesting(fixture.testRootDisposable)
        initialized(EmmetAbbreviationTestFixture(fixture, extension)) {}
      }

    private fun selectSourceData(sourceData: String): String {
      return if (sourceData.contains(EditorTestUtil.SELECTION_START_TAG)) sourceData
      else EditorTestUtil.SELECTION_START_TAG + sourceData + EditorTestUtil.SELECTION_END_TAG
    }
  }
}
