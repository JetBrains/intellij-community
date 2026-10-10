// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.devkit

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.python.testAssertions.PyTestAssertion
import com.intellij.python.testAssertions.PyTestAssertionFix
import com.intellij.python.testAssertions.PyTestAssertionToken
import com.intellij.python.testAssertions.PyTestDirectives

/**
 * Checks and colours the assertion language of `PyCodeInsightTestCase` in an injected Python text.
 *
 * It shows the problems that the parser finds, so a test does not have to run to find them.
 * It also warns about a marker that does not point at whole tokens of the code line.
 * It annotates the whole injected file at one time, because a problem can also be outside a comment.
 * For example, a `#` in a string literal can start an inline assertion.
 */
internal class PyTestAssertionAnnotator : Annotator {

  override fun annotate(element: PsiElement, holder: AnnotationHolder) {
    if (element !is PsiFile) return
    val testText = PyTestCaseText.of(element) ?: return

    for (diagnostic in testText.parseResult.diagnostics) {
      val end = maxOf(diagnostic.end, minOf(diagnostic.start + 1, element.textLength))
      @NlsSafe val problem = diagnostic.message
      val builder = holder.newAnnotation(HighlightSeverity.ERROR, PyDevKitBundle.message("test.assertion.problem", problem))
        .range(TextRange(diagnostic.start, end))
      diagnostic.fix?.let { builder.withFix(AssertionFix(element, it)) }
      builder.create()
    }

    for (token in testText.parseResult.tokens) {
      holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
        .range(TextRange(token.start, token.end))
        .textAttributes(textAttributes(token.kind))
        .create()
    }

    for (assertion in testText.parseResult.assertions) {
      val markerRange = testText.markerRange(assertion) ?: continue
      val problem = markerProblem(element, testText, assertion) ?: continue
      holder.newAnnotation(HighlightSeverity.WEAK_WARNING, problem).range(markerRange).create()
    }
  }

  /**
   * Returns a problem of the marker of [assertion], or null.
   *
   * Only a directive that selects an element gets the check. The test itself compares the range of a highlight.
   * A `CARET` can stand on a space.
   */
  private fun markerProblem(file: PsiFile, testText: PyTestCaseText, assertion: PyTestAssertion): String? {
    val directive = assertion.directive ?: return null
    if (directive.isSeverity || directive == PyTestDirectives.CARET || directive == PyTestDirectives.ISSUES) return null
    val codeRange = testText.codeRange(assertion) ?: return null
    val startLeaf = file.findElementAt(codeRange.startOffset) ?: return null
    if (startLeaf is PsiWhiteSpace) return PyDevKitBundle.message("test.assertion.marker.on.space")
    if (codeRange.length == 1 || isTextInside(startLeaf)) return null
    if (startLeaf.textRange.startOffset != codeRange.startOffset) return PyDevKitBundle.message("test.assertion.marker.span.start")
    val endLeaf = file.findElementAt(codeRange.endOffset - 1) ?: return null
    if (!isTextInside(endLeaf) && endLeaf.textRange.endOffset != codeRange.endOffset) {
      return PyDevKitBundle.message("test.assertion.marker.span.end")
    }
    return null
  }

  /** Returns true for a string literal or a comment. A marker can point at a part of such a token, for example a type in a string. */
  private fun isTextInside(leaf: PsiElement): Boolean =
    leaf is PsiComment || PsiTreeUtil.getParentOfType(leaf, PsiLanguageInjectionHost::class.java, false) != null

  private fun textAttributes(kind: PyTestAssertionToken.Kind): TextAttributesKey = when (kind) {
    PyTestAssertionToken.Kind.MARKER -> DefaultLanguageHighlighterColors.DOC_COMMENT_MARKUP
    PyTestAssertionToken.Kind.DIRECTIVE -> DefaultLanguageHighlighterColors.DOC_COMMENT_TAG
    PyTestAssertionToken.Kind.MODIFIER -> DefaultLanguageHighlighterColors.DOC_COMMENT_TAG
  }

  /** Applies the edit of a [PyTestAssertionFix] to the text. */
  private class AssertionFix(file: PsiFile, private val fix: PyTestAssertionFix) : PsiUpdateModCommandAction<PsiFile>(file) {

    override fun getFamilyName(): String = PyDevKitBundle.message("test.assertion.fix.family")

    override fun getPresentation(context: ActionContext, element: PsiFile): Presentation = Presentation.of(when (fix.kind) {
      PyTestAssertionFix.Kind.DIRECTIVE_NAME -> PyDevKitBundle.message("test.assertion.fix.replace.directive", fix.replacement)
      PyTestAssertionFix.Kind.CANONICAL_FORM -> PyDevKitBundle.message("test.assertion.fix.canonical.form")
    })

    override fun invoke(context: ActionContext, element: PsiFile, updater: ModPsiUpdater) {
      element.fileDocument.replaceString(fix.start, fix.end, fix.replacement)
    }
  }
}
