// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.emmet

import com.intellij.application.options.emmet.EmmetOptions
import com.intellij.codeInsight.template.CustomTemplateCallback
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.codeInsight.template.emmet.filters.ZenCodingFilter
import com.intellij.codeInsight.template.emmet.tokens.TemplateToken
import com.intellij.codeInsight.template.impl.TemplateImpl
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.codeInsight.template.impl.TemplateSettings
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightProjectFixture
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.awaitPendingNavigationAndEditorOpenIfEnabled
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.Assertions.assertEquals

/**
 * The helpers of an Emmet test: they expand an abbreviation, wrap a selection, update a tag, and apply a filter.
 * The test file of each helper has the name `test.<extension>`.
 * Create it with [emmetAbbreviationFixture].
 * A parameterized test runs the cases of [EmmetAbbreviationTestCases] with [checkAbbreviation].
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
   * Runs [testCase]: it applies the initializer of the case, expands the abbreviation with the Tab action,
   * goes through the tab stops of the template, reformats the file, and compares the result with the expected text.
   * When the expected text has no caret marker, the expected text goes through the same expansion and reformat first.
   * After the test, it resets the [EmmetOptions].
   */
  fun checkAbbreviation(testCase: EmmetAbbreviationTestCase) {
    Disposer.register(fixture.testRootDisposable) { EmmetOptions.getInstance().loadState(EmmetOptions()) }
    testCase.initializer?.init(fixture, fixture.testRootDisposable)
    val expectedTabStops = HashSet<Int>()
    val actualTabStops = HashSet<Int>()
    val expectedText = if (testCase.expected.contains(EditorTestUtil.CARET_TAG)) testCase.expected
    else prepareExpectedText(testCase.expected, testCase.extension, expectedTabStops)
    fixture.configureByText("test.${testCase.extension}", testCase.source)
    runInEdtAndWait {
      if (!testCase.source.contains(EditorTestUtil.CARET_TAG)) {
        fixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_LINE_END)
      }
      expandAndReformat(actualTabStops)
    }
    fixture.checkResult(expectedText)
    if (testCase.checkPosition) {
      assertEquals(expectedTabStops, actualTabStops)
    }
  }

  private fun prepareExpectedText(expectedData: String, extension: String, tabStops: MutableSet<Int>): String {
    var text = expectedData
    while (true) {
      val index = text.indexOf('|')
      if (index < 0) break
      text = text.substring(0, index) + text.substring(index + 1)
      tabStops.add(index)
    }
    fixture.configureByText("expected.$extension", text)
    return runInEdtAndGet {
      fixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_LINE_END)
      expandAndReformat(null)
      file.text
    }
  }

  /**
   * Expands the abbreviation at the caret, finishes the template, and reformats the file.
   * Adds the caret offset of each tab stop to [tabStops]. Call it on the EDT.
   */
  private fun expandAndReformat(tabStops: MutableSet<Int>?) {
    val action = ActionManager.getInstance().getAction(IdeActions.ACTION_EXPAND_LIVE_TEMPLATE_BY_TAB) as EditorAction
    action.actionPerformed(editor, DataManager.getInstance().dataContext)
    waitForAsyncTasks()
    WriteCommandAction.runWriteCommandAction(project) {
      val state = TemplateManagerImpl.getTemplateState(editor)
      val topLevelEditor = InjectedLanguageEditorUtil.getTopLevelEditor(editor)
      while (state != null && !state.isFinished) {
        tabStops?.add(topLevelEditor.caretModel.offset)
        state.nextTab()
      }
      tabStops?.add(topLevelEditor.caretModel.offset)
      CodeStyleManager.getInstance(project).reformat(file)
    }
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
      emmetAbbreviationFixture(codeInsightProjectFixture(), extension)

    /**
     * Creates an [EmmetAbbreviationTestFixture] for test files with [extension] in the project of [projectFixture].
     * The project must be ready for [codeInsightFixture], for example a [codeInsightProjectFixture].
     * Declare [projectFixture] in a companion object, or as a static field in Java, to share one project and its module
     * between the tests of a class. Then the next test sees the files of an earlier test.
     * Declare the result as an instance field.
     */
    @JvmStatic
    fun emmetAbbreviationFixture(projectFixture: TestFixture<Project>, extension: String): TestFixture<EmmetAbbreviationTestFixture> =
      testFixture("emmetAbbreviationFixture") {
        val fixture = codeInsightFixture(projectFixture).init()
        TemplateManagerImpl.setTemplateTesting(fixture.testRootDisposable)
        initialized(EmmetAbbreviationTestFixture(fixture, extension)) {}
      }

    private fun selectSourceData(sourceData: String): String {
      return if (sourceData.contains(EditorTestUtil.SELECTION_START_TAG)) sourceData
      else EditorTestUtil.SELECTION_START_TAG + sourceData + EditorTestUtil.SELECTION_END_TAG
    }
  }
}
