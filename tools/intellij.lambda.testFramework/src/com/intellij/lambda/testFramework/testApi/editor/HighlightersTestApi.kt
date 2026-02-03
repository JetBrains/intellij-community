package com.intellij.lambda.testFramework.testApi.editor

import com.intellij.codeInsight.daemon.impl.SeverityRegistrar
import com.intellij.codeInsight.template.impl.TemplateState
import com.intellij.lambda.testFramework.frameworkLogger
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.MarkupModel
import com.intellij.openapi.project.Project
import com.intellij.remoteDev.tests.LambdaFrontendContext
import com.intellij.remoteDev.tests.LambdaIdeContext
import com.intellij.remoteDev.tests.impl.utils.waitSuspending
import org.assertj.core.api.Assertions.assertThat
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

context(lambdaIdeContext: LambdaIdeContext)
fun EditorImpl.getHighlighters(
  startLine: Int = 1, endLine: Int? = null, filter: (RangeHighlighterEx) -> Boolean = { true },
): List<RangeHighlighterEx> {
  val lineStartOffset = document.getLineStartOffset(startLine - 1)
  val lineEndOffset = document.getLineEndOffset((endLine ?: document.lineCount) - 1)
  val highlighters = mutableListOf<RangeHighlighterEx>()

  val documentMarkupModel = DocumentMarkupModel.forDocument(document, project, true) as MarkupModelEx
  val editorMarkupModel = markupModel
  listOf(documentMarkupModel, editorMarkupModel).forEach {
    it.processRangeHighlightersOverlappingWith(lineStartOffset, lineEndOffset) {
      if (filter(it)) {
        highlighters.add(it)
      }
      true
    }
  }

  frameworkLogger.info("Requesting highlighters from line #$startLine till " + (endLine?.let { "line #$endLine" }
                                                                                ?: "the end") + "; found ${highlighters.size}")
  return highlighters
}

context(lambdaIdeContext: LambdaIdeContext)
fun EditorImpl.getJavaKeyWordHighlighters(filter: (RangeHighlighterEx) -> Boolean = { true }): List<RangeHighlighterEx> =
  getHighlighters { filter(it) && it.textAttributesKey?.externalName == "JAVA_KEYWORD" }

context(lambdaIdeContext: LambdaIdeContext)
fun EditorImpl.getBracketsHighlighters(): List<RangeHighlighterEx> =
  getHighlighters {
    it.textAttributesKey == CodeInsightColors.MATCHED_BRACE_ATTRIBUTES ||
    it.textAttributesKey == CodeInsightColors.UNMATCHED_BRACE_ATTRIBUTES
  }


val isExecutionPointHighlighters: (RangeHighlighterEx) -> Boolean = { highlighter ->
  highlighter.textAttributesKey == TextAttributesKey.find("EXECUTIONPOINT_ATTRIBUTES")
}

context(lambdaIdeContext: LambdaIdeContext)
fun EditorImpl.lineHasBackgroundColor(lineNumber: Int): Boolean =
  getHighlighters(startLine = lineNumber, endLine = lineNumber) { it.getTextAttributes(colorsScheme)?.backgroundColor != null }.isNotEmpty()

context(lambdaIdeContext: LambdaIdeContext)
fun EditorImpl.getHighlightersWithGutter(startLine: Int = 1, endLine: Int? = null, filter: (RangeHighlighterEx) -> Boolean = { true }): List<RangeHighlighterEx> =
  getHighlighters(startLine, endLine) { it.gutterIconRenderer != null && filter(it) }

//e.g. live template or refactor popup
suspend fun EditorImpl.waitForRangeTemplateHighlighter(timeout: Duration = 20.seconds) {
  waitSuspending("Editor has live template highlighter", timeout) {
    hasRangeTemplateHighlighter(markupModel)
  }
}

suspend fun EditorImpl.waitNoRangeTemplateHighlighter(timeout: Duration = 20.seconds) {
  waitSuspending("Editor doesn't have live template highlighter", timeout) {
    !hasRangeTemplateHighlighter(markupModel)
  }
}

context(lambdaIdeContext: LambdaIdeContext)
suspend fun EditorImpl.waitHighlighters(timeout: Duration = 20.seconds, startLine: Int = 1, endLine: Int? = null, filter: (RangeHighlighterEx) -> Boolean = { true }) =
  waitSuspending("Wait for suitable highlighters", timeout,
                 getter = { getHighlighters(startLine, endLine, filter) },
                 checker = { it.isNotEmpty() })

private fun hasRangeTemplateHighlighter(markupModel: MarkupModel): Boolean {
  if (markupModel !is MarkupModelEx) {
    return false
  }
  return markupModel.allHighlighters.any { highlighter ->
    highlighter.getUserData(TemplateState.TEMPLATE_RANGE_HIGHLIGHTER_KEY) == true
  }
}

data class BuiltInHighlighter(val start: Int, val end: Int, val textAttributes: List<TextAttributesKey>)

context(frontendContext: LambdaFrontendContext)
fun EditorImpl.getBuiltInHighlighters(): List<BuiltInHighlighter> {
  val builtInHighlighters = mutableListOf<BuiltInHighlighter>()
  val editorHighlighter = (this as EditorEx).highlighter as LexerEditorHighlighter

  // With the help of Mikhail Pyltsin:
  // this logic is hidden it is hidden by highlighter and logic is repeated from com.jetbrains.rdserver.daemon.syntax.MarkupBasedLexerHighlighting.updateSyntaxHighlighters
  val iterator = editorHighlighter.createIterator(0)
  while (!iterator.atEnd()) {
    builtInHighlighters.add(BuiltInHighlighter(iterator.start, iterator.end, iterator.textAttributesKeys.toList()))
    iterator.advance()
  }

  return builtInHighlighters
}


context(frontendContext: LambdaFrontendContext)
fun List<BuiltInHighlighter>.checkTokensHighlighted(tokens: List<String>): List<BuiltInHighlighter> {
  frameworkLogger.info("Check that the text has highlightings: $tokens")
  frameworkLogger.info("builtInHighlighters=$this")

  assertThat(this.map { it.textAttributes.map { it.externalName } }.flatten())
    .describedAs("Tokens $tokens are highlighted")
    .containsAll(tokens)

  return this
}

context(frontendContext: LambdaFrontendContext)
fun List<BuiltInHighlighter>.checkElementHighlighted(
  token: String,
  startOffset: Int,
  endOffset: Int,
  description: String,
): List<BuiltInHighlighter> {
  frameworkLogger.info("Check that element with token $token highlighted between offsets [$startOffset, $endOffset]")
  frameworkLogger.info("builtInHighlighters=$this")

  assertThat(this)
    .describedAs(description)
    .filteredOn { it.textAttributes.any { it.externalName.contains(token) } }
    .isNotEmpty
    .filteredOn { it.start == startOffset && it.end == endOffset }
    .singleElement()

  return this
}

val Project?.trafficLightErrorIndex: Int
  get() {
    return getSeverityIndex(HighlightSeverity.ERROR)
  }

private fun Project?.getSeverityIndex(severity: HighlightSeverity): Int {
  if (this == null) return 0
  val severityRegistrar = SeverityRegistrar.getSeverityRegistrar(this)
  return getSeverityIndex(severityRegistrar, severity)
}

val Project?.trafficLightWarningIndex: Int
  get() {
    return getSeverityIndex(HighlightSeverity.WARNING)
  }

private fun getSeverityIndex(severityRegistrar: SeverityRegistrar, severity: HighlightSeverity): Int {
  var i = 0
  while (true) {
    val severityByIndex = severityRegistrar.getSeverityByIndex(i)
    if (severityByIndex == null || severityByIndex == severity) {
      break
    }
    i++
  }
  return i
}
