// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor.livepreview

import com.intellij.markdown.frontend.editor.livepreview.MarkdownLivePreviewReconciler
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.psi.PsiManager
import com.intellij.testFramework.PerformanceUnitTest
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.tools.ide.metrics.benchmark.Benchmark
import kotlinx.coroutines.Dispatchers
import org.intellij.plugins.markdown.MarkdownTestingUtil
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.lang.reflect.Method
import kotlin.time.Duration.Companion.minutes

@PerformanceUnitTest
@TestApplication
internal class MarkdownLivePreviewPerformanceTest {
  private val myFixture by codeInsightModuleFixture(projectFixture)

  @TestDisposable
  lateinit var disposable: Disposable

  private lateinit var testMethod: Method

  @BeforeEach
  fun setUp(testInfo: TestInfo) {
    testMethod = testInfo.testMethod.get()
    myFixture.testDataPath = MarkdownTestingUtil.TEST_DATA_PATH + "/performance"
    EditorFactory.getInstance().addEditorFactoryListener(object : EditorFactoryListener {
      override fun editorCreated(event: EditorFactoryEvent) {
        if (event.editor.editorKind == EditorKind.UNTYPED) event.editor.enableLivePreview()
      }
    }, disposable)
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(Sample::class)
  fun `open a large file with live preview`(sample: Sample): Unit = onEdt {
    val file = myFixture.copyFileToProject(sample.file)
    benchmark("${sample.file}: open", setup = {
      FileEditorManager.getInstance(myFixture.project).closeFile(file)
      PsiManager.getInstance(myFixture.project).dropPsiCaches()
    }) {
      myFixture.configureFromExistingVirtualFile(file)
      waitForPresentation(myFixture.editor, true)
    }
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(Sample::class)
  fun `switch live preview on and off`(sample: Sample): Unit = onEdt {
    val editor = open(sample)
    benchmark("${sample.file}: turn on", setup = { switchLivePreview(editor, false) }) { switchLivePreview(editor, true) }
    benchmark("${sample.file}: turn off", setup = { switchLivePreview(editor, true) }) { switchLivePreview(editor, false) }
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(Sample::class)
  fun `reveal and conceal elements at offset`(sample: Sample): Unit = onEdt {
    val editor = open(sample)
    // The line before the first target is blank, so a caret there touches no element.
    val away = sample.caretOffsets.first() - 1
    for (target in sample.caretOffsets) {
      assertRevealsFolds(editor, away, target)
      benchmark("${sample.file}: reveal at $target", setup = { moveCaret(editor, away) }) { moveCaret(editor, target) }
      benchmark("${sample.file}: conceal at $target", setup = { moveCaret(editor, target) }) { moveCaret(editor, away) }
    }
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(Sample::class)
  fun `type at offset with live preview`(sample: Sample): Unit = onEdt {
    benchmark("${sample.file}: type", setup = {
      val editor = open(sample)
      val offset = sample.caretOffsets.first()
      val endOffset = editor.document.getLineEndOffset(editor.document.getLineNumber(offset))
      editor.caretModel.moveToOffset(endOffset)
    }) {
      myFixture.type('x')
    }
    myFixture.doHighlighting()
    waitForPresentation(myFixture.editor, true)
  }

  private fun onEdt(body: () -> Unit): Unit = timeoutRunBlocking(TIMEOUT, context = Dispatchers.EDT) { body() }

  private fun benchmark(name: String, setup: () -> Unit = {}, body: () -> Unit) {
    Benchmark.newBenchmark(name) { body() }
      .setup { setup() }
      .runAsStressTest()
      .start(testMethod, name)
  }

  private fun open(sample: Sample): Editor {
    myFixture.configureByFile(sample.file)
    myFixture.doHighlighting()
    waitForPresentation(myFixture.editor, true)
    return myFixture.editor
  }

  private fun switchLivePreview(editor: Editor, enabled: Boolean) {
    editor.setLivePreviewEnabledness(enabled)
    waitForPresentation(editor, enabled)
  }

  private fun waitForPresentation(editor: Editor, presented: Boolean) {
    PlatformTestUtil.waitWithEventsDispatching(
      "Live preview did not reach presented=$presented",
      { (MarkdownLivePreviewReconciler.getExisting(editor)?.hasCurrentSpecs() == true) == presented },
      TIMEOUT.inWholeSeconds.toInt(),
    )
  }

  private fun moveCaret(editor: Editor, offset: Int) {
    editor.caretModel.moveToOffset(offset)
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
  }

  private fun assertRevealsFolds(editor: Editor, away: Int, target: Int) {
    moveCaret(editor, away)
    val concealed = editor.foldingModel.allFoldRegions.size
    moveCaret(editor, target)
    val revealed = editor.foldingModel.allFoldRegions.size
    assertTrue(revealed < concealed) { "The caret at $target must reveal folds: $concealed concealed, $revealed revealed" }
  }

  @Suppress("unused")
  enum class Sample(val file: String, vararg val caretOffsets: Int) {
    CHANGELOG("CHANGELOG.md", 28),
    INDEX("index.md", 380, 1536);

    override fun toString(): String = file
  }

  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
    private val TIMEOUT = 10.minutes
  }
}
