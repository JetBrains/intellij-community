// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked

import com.intellij.execution.filters.ConsoleFilterProvider
import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.Filter.ResultItem
import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.execution.impl.InlayProvider
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.tests.reworked.util.TerminalTestUtil.update
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.text.allOccurrencesOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.terminal.JBTerminalSystemSettingsProvider
import org.jetbrains.plugins.terminal.block.reworked.TerminalOutputModel
import org.jetbrains.plugins.terminal.block.reworked.TerminalOutputModelImpl
import org.jetbrains.plugins.terminal.block.reworked.hyperlinks.TerminalHyperlinkHighlighter
import org.jetbrains.plugins.terminal.block.ui.TerminalUiUtils
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@RunWith(JUnit4::class)
internal class TerminalHyperlinkHighlighterTest : BasePlatformTestCase() {

  private lateinit var editor: Editor
  private lateinit var model: TerminalOutputModel
  private lateinit var hyperlinkHighlighter: TerminalHyperlinkHighlighter

  override fun runInDispatchThread(): Boolean = false

  private suspend fun CoroutineScope.init(vararg filter: Filter, maxCapacity: Int = 0) {
    val coroutineScope = this
    useFilters(*filter)
    withContext(Dispatchers.EDT) {
      editor = createEditor()
      model = TerminalOutputModelImpl(editor.document, maxCapacity)
      hyperlinkHighlighter = TerminalHyperlinkHighlighter.install(project, model, editor, coroutineScope)
    }
    hyperlinkHighlighter.awaitInitialized()
  }

  private fun useFilters(vararg filter: Filter) {
    ExtensionTestUtil.maskExtensions<ConsoleFilterProvider>(
      ConsoleFilterProvider.FILTER_PROVIDERS,
      listOf(ConsoleFilterProvider { filter }),
      testRootDisposable
    )
  }

  private fun createEditor(): Editor {
    val document = DocumentImpl("", true)
    val editor = TerminalUiUtils.createOutputEditor(document, project, JBTerminalSystemSettingsProvider(), false)
    Disposer.register(testRootDisposable) {
      EditorFactory.getInstance().releaseEditor(editor)
    }
    return editor
  }

  @Test
  fun `basic hyperlinks`() = timeoutRunBlockingInBackground {
    val linkText = "foo"
    init(MyHyperlinkFilter(linkText))
    withContext(Dispatchers.EDT) {
      model.update(0, listOf(
        "Hello",
        linkText,
        "World",
        linkText
      ).joinLines(), emptyList())
      assertHyperlinks(linkText, 2)
    }
  }

  @Test
  fun `second update removes trailing text`() = timeoutRunBlockingInBackground {
    val linkText = "my super link"
    init(MyHyperlinkFilter(linkText))
    val patternLines = (1..3).map { "-".repeat(it) + linkText + "-".repeat(it) } + listOf("foo")
    val allLines = (1..10).flatMap { patternLines }
    withContext(Dispatchers.EDT) {
      model.update(0, (allLines + listOf("extra trailing output")).joinLines())
      model.update((allLines.size - 1).toLong(), allLines.last())
      assertEquals(allLines.joinLines(), editor.document.text)
      assertHyperlinks(linkText, 3 * 10)
    }
  }

  @Test
  fun `overlapping updates with trimming top`() = timeoutRunBlockingInBackground {
    val fooLinkText = "foo"
    val barLinkText = "bar"

    val fooLines = (1..10).flatMap {
      (1..5).map { "-".repeat(it) + " " + fooLinkText }
    }

    val barLines = (1..10).flatMap {
      (1..5).map { "-".repeat(it) + " " + barLinkText }
    }

    init(
      MyHyperlinkFilter(fooLinkText, true), MyHyperlinkFilter(barLinkText, true),
      maxCapacity = fooLines.joinLines().length
    )

    withContext(Dispatchers.EDT) {
      model.update(0, fooLines.joinLines())

      for (i in 0..barLines.size - 2) {
        model.update((fooLines.size - 1 + i).toLong(), listOf(barLines[i], barLines[i + 1]).joinLines())
      }

      assertEquals(barLines.joinLines(), editor.document.text)
      assertHyperlinks(barLinkText, 5 * 10)
    }
  }

  @Test
  fun `overlapping updates with trimming top and removing trailing text`() = timeoutRunBlockingInBackground {
    val linkText = "my-link"
    val pattern = listOf("X $linkText", "$linkText X", linkText)
    val lines: List<String> = (1..100).flatMap { pattern }

    init(MyHyperlinkFilter(linkText, true), maxCapacity = 100)

    withContext(Dispatchers.EDT) {
      model.update(0, lines.joinLines() + " some trailing output")

      for (startLine in listOf(50, 100, 200)) {
        model.update(startLine.toLong(), lines.subList(startLine, lines.size).joinLines())
      }

      assertEquals(lines.joinLines().takeLast(100), editor.document.text)
      assertHyperlinks(linkText, 10)
    }
  }

  @Test
  fun `test document ending with newline`() = timeoutRunBlockingInBackground {
    val linkText = "my-link"
    val pattern = listOf("X $linkText", "$linkText X", linkText, "")
    val lines: List<String> = (1..10).flatMap { pattern }

    init(MyHyperlinkFilter(linkText, true), maxCapacity = 50)

    withContext(Dispatchers.EDT) {
      for (startLine in listOf(0, 10, 20, 30)) {
        model.update(startLine.toLong(), lines.subList(startLine, lines.size).joinLines())
      }

      assertEquals(lines.joinLines().takeLast(50), editor.document.text)
      assertHyperlinks(linkText, 5)
    }
  }

  @Test
  fun `hyperlinks in trimmed output 1`() = timeoutRunBlockingInBackground {
    val linkText = "abcdef"
    init(MyHyperlinkFilter(linkText), maxCapacity = 1024)
    withContext(Dispatchers.EDT) {
      model.update(0, joinLines(
        linkText,
        *Array(1024) { it.toString() },
        linkText
      ), emptyList())
      assertHyperlinks(linkText, 1)
    }
  }

  @Test
  fun `hyperlinks in trimmed output 2`() = timeoutRunBlockingInBackground {
    val linkText = "abcdefg"
    init(MyHyperlinkFilter(linkText), maxCapacity = 1024)
    withContext(Dispatchers.EDT) {
      model.update(
        0,
        (1..1024).map {
          if (it % 2 == 0) linkText else "-"
        }.joinLines()
      )
      // 102 = (1024 + "\n-\n".length) / "$linkText\n-\n".length
      assertHyperlinks(linkText, 102)
    }
  }

  @Test
  fun `test several updates without changes`() = timeoutRunBlockingInBackground {
    val linkText = "my-link"
    val inlayText = "my-inlay"
    val pattern = listOf("X $linkText $inlayText", "- $linkText", "")
    val lines: List<String> = (1..2).flatMap { pattern }

    val maxCapacity = lines.joinLines().length
    init(
      MyHyperlinkFilter(linkText, true), MyInlayFilter(inlayText, true),
      maxCapacity = maxCapacity
    )

    withContext(Dispatchers.EDT) {
      repeat(20) {
        model.update(0, lines.joinLines())
        if (it % 4 == 0) {
          assertEquals(lines.joinLines().takeLast(maxCapacity), editor.document.text)
          assertHyperlinks(linkText, 4)
          assertInlays(inlayText, 2)
        }
      }
    }
  }

  @Test
  fun `overlapping updates with trimming top and awaiting links (hyperlink+inlay)`() = timeoutRunBlockingInBackground {
    val linkText = "my link"
    val inlayText = "my inlay"
    val pattern = listOf("- $linkText $inlayText", "-- $linkText", "--- $linkText")
    val lines: List<String> = (1..200).flatMap { pattern }

    val maxCapacity = (pattern + pattern + pattern).joinLines().length
    init(
      MyHyperlinkFilter(linkText, true), MyInlayFilter(inlayText, true),
      maxCapacity = maxCapacity
    )

    withContext(Dispatchers.EDT) {
      for (i in 3..lines.size - 1) {
        val overlappingBottomLines = i % 3
        model.update((i - overlappingBottomLines).toLong(), lines.subList(i - overlappingBottomLines, i + 1).joinLines())
        if (i % 20 == 0) {
          assertEquals(lines.subList(0, i + 1).joinLines().takeLast(maxCapacity), editor.document.text)
          assertHyperlinks(linkText, 3 * 3)
          assertInlays(inlayText, 3)
        }
      }
      assertEquals(lines.joinLines().takeLast(maxCapacity), editor.document.text)
      assertHyperlinks(linkText, 3 * 3)
      assertInlays(inlayText, 3)
    }
  }

  private suspend fun assertHyperlinks(linkText: String, expectedCount: Int): List<RangeHighlighter> {
    val text = editor.document.text
    val expectedRanges = text.allOccurrencesOf(linkText).map {
      TextRange(it, it + linkText.length)
    }.toList()
    val rangeHighlighters = awaitHyperlinks(AWAIT_FILTER_TIMEOUT)
    for (rangeHighlighter in rangeHighlighters) {
      assertEquals(linkText, rangeHighlighter.textRange.substring(text))
    }
    assertEquals(expectedRanges, rangeHighlighters.map { it.textRange })
    assertEquals(expectedCount, rangeHighlighters.size)
    return rangeHighlighters
  }

  private suspend fun awaitHyperlinks(timeout: Duration): List<RangeHighlighter> {
    hyperlinkHighlighter.awaitDelayedHighlightings()
    val hyperlinkSupport = hyperlinkHighlighter.hyperlinkSupport
    hyperlinkSupport.waitForPendingFilters(timeout.inWholeMilliseconds)
    return hyperlinkSupport.getAllHyperlinks(0, editor.document.textLength)
  }

  private suspend fun assertInlays(inlayText: String, expectedCount: Int): List<Inlay<*>> {
    val text = editor.document.text
    val expectedOffsets = text.allOccurrencesOf(inlayText).map { it + inlayText.length }.toList()
    val inlays = awaitInlays(AWAIT_FILTER_TIMEOUT)
    val actualOffsets = inlays.map { it.offset }
    for (offset in actualOffsets) {
      assertEquals(inlayText, TextRange(offset - inlayText.length, offset).substring(text))
    }
    assertEquals(expectedOffsets, actualOffsets)
    assertEquals(expectedCount, inlays.size)
    return inlays
  }

  private suspend fun awaitInlays(timeout: Duration): List<Inlay<*>> {
    hyperlinkHighlighter.awaitDelayedHighlightings()
    val hyperlinkSupport = hyperlinkHighlighter.hyperlinkSupport
    hyperlinkSupport.waitForPendingFilters(timeout.inWholeMilliseconds)
    return hyperlinkSupport.collectAllInlays()
  }
}

private fun <T> timeoutRunBlockingInBackground(action: suspend CoroutineScope.() -> T) {
  timeoutRunBlocking(timeout = 30.seconds, context = Dispatchers.Default) {
    val testScope = childScope("child scope to run the test")
    try {
      testScope.action()
    }
    finally {
      testScope.cancel() // stop async coroutines launched by the test
    }
  }
}

private fun joinLines(vararg lines: String): String = lines.joinToString("\n")

private fun List<String>.joinLines(): String = this.joinToString("\n")

private class MyHyperlinkFilter(linkText: String, delay: Boolean = false) : MyFilter(linkText, delay) {
  override fun createResultItem(startOffset: Int, endOffset: Int): ResultItem {
    return ResultItem(startOffset, endOffset, MyHyperlinkInfo(linkText), null)
  }

  data class MyHyperlinkInfo(val linkText: String) : HyperlinkInfo {
    override fun navigate(project: Project) {}
  }
}

private class MyInlayFilter(linkText: String, delay: Boolean = false) : MyFilter(linkText, delay) {
  override fun createResultItem(startOffset: Int, endOffset: Int): ResultItem {
    return MyInlay(startOffset, endOffset)
  }

  private class MyInlay(highlightStartOffset: Int, highlightEndOffset: Int) : ResultItem(highlightStartOffset, highlightEndOffset, null),
                                                                              InlayProvider {
    override fun createInlay(editor: Editor, offset: Int): Inlay<*>? {
      return editor.inlayModel.addInlineElement(offset, EditorCustomElementRenderer { 42 })
    }
  }
}

private abstract class MyFilter(val linkText: String, val delay: Boolean) : Filter, DumbAware {
  override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
    val startInd = line.indexOf(linkText)
    if (startInd == -1) return null
    if (delay) {
      // emulate real filter delay
      Thread.sleep(1)
    }
    val startOffset = entireLength - line.length + startInd
    val endOffset = startOffset + linkText.length
    return Filter.Result(listOf(createResultItem(startOffset, endOffset)))
  }

  abstract fun createResultItem(startOffset: Int, endOffset: Int): ResultItem
}

private val AWAIT_FILTER_TIMEOUT: Duration = 30.seconds
