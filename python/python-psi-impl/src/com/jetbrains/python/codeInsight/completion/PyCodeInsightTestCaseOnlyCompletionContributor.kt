package com.jetbrains.python.codeInsight.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.codeInsight.completion.PyTestAssertionParserSymbols.FIXME_KEYWORD
import com.jetbrains.python.codeInsight.completion.PyTestAssertionParserSymbols.GUIDE_BAR
import com.jetbrains.python.codeInsight.completion.PyTestAssertionParserSymbols.GUIDE_BAR_ASCII
import com.jetbrains.python.codeInsight.completion.PyTestAssertionParserSymbols.MARKER_CORNER
import com.jetbrains.python.codeInsight.completion.PyTestAssertionParserSymbols.MARKER_LEFT
import com.jetbrains.python.codeInsight.completion.PyTestAssertionParserSymbols.MARKER_SPAN
import com.jetbrains.python.codeInsight.completion.PyTestAssertionParserUtils.defaultSeverityNames
import com.jetbrains.python.codeInsight.completion.PyTestAssertionParserUtils.isAssertionMarker
import com.jetbrains.python.codeInsight.completion.PyTestAssertionParserUtils.scanTokenEnd
import com.jetbrains.python.codeInsight.completion.PyTestAssertionParserUtils.skipWhitespace
import com.jetbrains.python.codeInsight.completion.PyTestAssertionParserUtils.skipWhitespaceAndGuides


enum class PyTestAssertionType {
  TYPE,
  IS_BUILTIN,
  EXPECTED_VARIANCE,
  INFERRED_VARIANCE,
  ISSUES,
  ;

  companion object {
    fun fromValue(value: String): PyTestAssertionType? {
      return entries.find { it.name.equals(value, ignoreCase = true) }
    }
  }
}


object PyTestAssertionParserSymbols {
  const val COMMENT_CHAR: Char = '#'
  const val MARKER_CORNER: Char = '└'
  const val MARKER_LEFT: Char = '\\'
  const val MARKER_SPAN: Char = '^'
  const val GUIDE_BAR: Char = '│'
  const val GUIDE_BAR_ASCII: Char = '|'
  const val NEWLINE: Char = '\n'
  const val FIXME_KEYWORD: String = "FIXME"
}


object PyTestAssertionParserUtils {
  val defaultSeverityNames: List<String> = HighlightSeverity.DEFAULT_SEVERITIES.map { it.name }

  fun skipWhitespace(text: String, startIndex: Int): Int {
    var i = startIndex
    while (i < text.length && text[i].isWhitespace()) {
      i++
    }
    return i
  }

  fun skipWhitespaceAndGuides(text: String): Int {
    var i = 0
    while (i < text.length && (text[i].isWhitespace() || isGuide(text[i]))) {
      i++
    }
    return i
  }

  fun isGuide(ch: Char): Boolean {
    return ch == GUIDE_BAR || ch == GUIDE_BAR_ASCII
  }

  fun scanTokenEnd(text: String, startIndex: Int): Int {
    var i = startIndex
    while (i < text.length && !text[i].isWhitespace()) {
      i++
    }
    return i
  }

  fun isAssertionMarker(ch: Char): Boolean {
    return ch == MARKER_CORNER || ch == MARKER_LEFT || ch == MARKER_SPAN || ch == GUIDE_BAR
  }
}


class PyCodeInsightTestCaseOnlyCompletionContributor : CompletionContributor() {

  init {
    extend(
      CompletionType.BASIC,
      PlatformPatterns.psiElement(),
      Provider,
    )
  }

  private enum class CompletionContext {
    COMMENT_ONLY_ASSERTION_START,
    COMMENT_ONLY_ASSERTION_START_WHITESPACE,
    INLINE_ASSERTION_START,
    COMMENT_ONLY_ASSERTION_AFTER_MARKER,
    ANY_ASSERTION_AFTER_TYPE,
  }

  private object Provider : CompletionProvider<CompletionParameters>() {
    private const val PY_CODE_INSIGHT_TEST_CASE = "PyCodeInsightTestCase"

    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
      val position = parameters.position
      if (!isInPyCodeInsightTestCaseContext(position)) {
        return
      }

      if (position.containingFile?.fileType != PythonFileType.INSTANCE) {
        return
      }

      if (!isInsidePythonComment(position)) {
        return
      }

      val completionContext = getCompletionContext(parameters) ?: return
      val resultWithoutPrefix = result.withPrefixMatcher("")
      val resultWithCurrentTokenPrefix = result.withPrefixMatcher(getCurrentTokenPrefix(parameters))

      when (completionContext) {
        CompletionContext.COMMENT_ONLY_ASSERTION_START -> {
          addMarkerElements(resultWithoutPrefix, includeLeftMarker = true)
        }
        CompletionContext.COMMENT_ONLY_ASSERTION_START_WHITESPACE -> {
          addMarkerElements(resultWithoutPrefix, includeLeftMarker = false)
        }
        CompletionContext.INLINE_ASSERTION_START,
        CompletionContext.COMMENT_ONLY_ASSERTION_AFTER_MARKER -> {
          addAssertionTypeElements(resultWithCurrentTokenPrefix)
        }
        CompletionContext.ANY_ASSERTION_AFTER_TYPE -> {
          addFixmeElement(resultWithCurrentTokenPrefix)
        }
      }
    }

    private fun getCompletionContext(parameters: CompletionParameters): CompletionContext? {
      val document = parameters.editor.document
      val offset = parameters.offset
      if (offset < 0 || offset > document.textLength) {
        return null
      }

      val lineNumber = document.getLineNumber(offset)
      val lineStartOffset = document.getLineStartOffset(lineNumber)
      val prefix = document.charsSequence.subSequence(lineStartOffset, offset).toString()
      val hashIndex = prefix.indexOf(PyTestAssertionParserSymbols.COMMENT_CHAR)
      if (hashIndex < 0) {
        return null
      }

      val beforeHash = prefix.substring(0, hashIndex)
      val afterHashBeforeCaret = prefix.substring(hashIndex + 1)
      val isCommentOnlyLine = beforeHash.isBlank()

      return if (isCommentOnlyLine) {
        getCommentOnlyCompletionContext(afterHashBeforeCaret)
      }
      else {
        getInlineCompletionContext(afterHashBeforeCaret)
      }
    }

    private fun getCommentOnlyCompletionContext(afterHashBeforeCaret: String): CompletionContext? {
      val cursor = skipWhitespaceAndGuides(afterHashBeforeCaret)

      if (cursor >= afterHashBeforeCaret.length) {
        return if (afterHashBeforeCaret.isEmpty()) {
          CompletionContext.COMMENT_ONLY_ASSERTION_START
        }
        else {
          CompletionContext.COMMENT_ONLY_ASSERTION_START_WHITESPACE
        }
      }

      val marker = afterHashBeforeCaret[cursor]
      if (!isAssertionMarker(marker)) {
        return null
      }

      val afterMarker = afterHashBeforeCaret.substring(cursor + 1)
      val afterMarkerTrimmed = afterMarker.trimStart()

      if (afterMarkerTrimmed.isEmpty()) {
        return CompletionContext.COMMENT_ONLY_ASSERTION_AFTER_MARKER
      }

      return if (hasCompletedTypeToken(afterMarkerTrimmed)) {
        CompletionContext.ANY_ASSERTION_AFTER_TYPE
      }
      else {
        CompletionContext.COMMENT_ONLY_ASSERTION_AFTER_MARKER
      }
    }

    private fun getInlineCompletionContext(afterHashBeforeCaret: String): CompletionContext? {
      if (afterHashBeforeCaret.isEmpty()) {
        return CompletionContext.INLINE_ASSERTION_START
      }

      val cursor = skipWhitespace(afterHashBeforeCaret, 0)
      if (cursor >= afterHashBeforeCaret.length) {
        return CompletionContext.COMMENT_ONLY_ASSERTION_START_WHITESPACE
      }

      val firstNonWhitespace = afterHashBeforeCaret[cursor]
      if (isAssertionMarker(firstNonWhitespace)) {
        val afterMarker = afterHashBeforeCaret.substring(cursor + 1).trimStart()

        if (afterMarker.isEmpty()) {
          return CompletionContext.COMMENT_ONLY_ASSERTION_AFTER_MARKER
        }

        return if (hasCompletedTypeToken(afterMarker)) {
          CompletionContext.ANY_ASSERTION_AFTER_TYPE
        }
        else {
          CompletionContext.COMMENT_ONLY_ASSERTION_AFTER_MARKER
        }
      }

      return if (hasCompletedTypeToken(afterHashBeforeCaret.trimStart())) {
        CompletionContext.ANY_ASSERTION_AFTER_TYPE
      }
      else {
        null
      }
    }

    private fun getCurrentTokenPrefix(parameters: CompletionParameters): String {
      val document = parameters.editor.document
      val offset = parameters.offset
      if (offset <= 0 || offset > document.textLength) {
        return ""
      }

      val lineNumber = document.getLineNumber(offset)
      val lineStartOffset = document.getLineStartOffset(lineNumber)
      val prefix = document.charsSequence.subSequence(lineStartOffset, offset).toString()

      val tokenStart = prefix.indexOfLast { it.isWhitespace() } + 1
      return prefix.substring(tokenStart)
    }

    private fun hasCompletedTypeToken(text: String): Boolean {
      val tokenEnd = scanTokenEnd(text, 0)
      if (tokenEnd <= 0) {
        return false
      }

      return tokenEnd < text.length && text[tokenEnd].isWhitespace()
    }

    private fun isInsidePythonComment(position: PsiElement): Boolean {
      if (PsiTreeUtil.getParentOfType(position, PsiComment::class.java, false) != null) {
        return true
      }

      val file = position.containingFile ?: return false
      val offsetBeforeCaret = position.textRange.startOffset - 1
      if (offsetBeforeCaret < 0) {
        return false
      }

      return PsiTreeUtil.getParentOfType(file.findElementAt(offsetBeforeCaret), PsiComment::class.java, false) != null
    }

    private fun addAssertionTypeElements(result: CompletionResultSet) {
      for (assertionType in PyTestAssertionType.entries) {
        result.addElement(
          LookupElementBuilder
            .create(assertionType.name)
            .withTypeText("PyTestAssertion", true)
        )
      }

      for (severityName in defaultSeverityNames) {
        result.addElement(
          LookupElementBuilder
            .create(severityName)
            .withTypeText("PyTestAssertion", true)
        )
      }
    }

    private fun addMarkerElements(result: CompletionResultSet, includeLeftMarker: Boolean) {
      result.addElement(
        LookupElementBuilder
          .create(MARKER_CORNER.toString())
          .withTypeText("PyTestAssertion", true)
          .withTailText(" indicates the symbol in the line above")
      )

      if (includeLeftMarker) {
        result.addElement(
          LookupElementBuilder
            .create(MARKER_LEFT.toString())
            .withTypeText("PyTestAssertion", true)
            .withTailText(" indicates the very first symbol in the line above")
        )
      }
    }

    private fun addFixmeElement(result: CompletionResultSet) {
      result.addElement(
        LookupElementBuilder
          .create(FIXME_KEYWORD)
          .withTypeText("PyTestAssertion", true)
          .withTailText(" followed by the fixed value")
      )
    }

    private fun isInPyCodeInsightTestCaseContext(element: PsiElement): Boolean {
      val context = element.containingFile?.context ?: return false
      return isInsideJavaPyCodeInsightTestCase(context) ||
             isInsideKotlinPyCodeInsightTestCase(context)
    }

    private fun isInsideJavaPyCodeInsightTestCase(context: PsiElement): Boolean {
      val psiClass = findParentByPsiClassName(context, "PsiClass") ?: return false
      return containsElementWithText(psiClass, PY_CODE_INSIGHT_TEST_CASE)
    }

    private fun isInsideKotlinPyCodeInsightTestCase(context: PsiElement): Boolean {
      val ktClass = findParentByPsiClassName(context, "KtClass") ?: return false
      return containsElementWithText(ktClass, PY_CODE_INSIGHT_TEST_CASE)
    }

    private fun findParentByPsiClassName(element: PsiElement, className: String): PsiElement? {
      // We deliberately compare only based on names to avoid dependencies to Java/Kotlin modules
      var current: PsiElement? = element
      while (current != null) {
        if (current.javaClass.simpleName == className) {
          return current
        }
        current = current.parent
      }
      return null
    }

    private fun containsElementWithText(element: PsiElement, text: String): Boolean {
      if (element.text == text) {
        return true
      }
      return element.children.any { containsElementWithText(it, text) }
    }
  }
}