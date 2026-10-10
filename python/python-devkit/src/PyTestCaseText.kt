// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.devkit

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.project.IntelliJProjectUtil
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.python.testAssertions.PyTestAssertion
import com.intellij.python.testAssertions.PyTestAssertionParseResult
import com.intellij.python.testAssertions.PyTestAssertionParser
import com.intellij.python.testAssertions.PyTestAssertionSyntax.NEWLINE
import com.intellij.python.testAssertions.PyTestAssertionText
import com.intellij.python.testAssertions.PyTestAssertionToken
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.toUElementOfType
import org.jetbrains.uast.visitor.AbstractUastVisitor

/**
 * A Python text with assertions that is injected into a test of the monorepo, with its parse result.
 *
 * The string must go to a parameter with [PyTestAssertionText]. All offsets are offsets in the injected file.
 */
internal class PyTestCaseText private constructor(val text: String) {
  val parseResult: PyTestAssertionParseResult = PyTestAssertionParser.parse(text)

  /** Returns the range of the marker of [assertion], or null for an inline assertion. */
  fun markerRange(assertion: PyTestAssertion): TextRange? {
    if (assertion.isInlineAssertion()) return null
    val marker = parseResult.tokens.find {
      it.kind == PyTestAssertionToken.Kind.MARKER && it.start in assertion.assertionOffsetStart until assertion.assertionOffsetEnd
    } ?: return null
    return TextRange(marker.start, marker.end)
  }

  /**
   * Returns the code range that the marker of [assertion] points at, or null for an inline assertion.
   *
   * The range is null when it does not fit into the code line. The parser reports such a marker.
   */
  fun codeRange(assertion: PyTestAssertion): TextRange? {
    if (assertion.isInlineAssertion()) return null
    val lineStart = assertion.codeOffsetStart - assertion.codeColumnStart
    val lineEnd = text.indexOf(NEWLINE, lineStart).takeIf { it >= 0 } ?: text.length
    val end = lineStart + maxOf(assertion.codeColumnEnd, assertion.codeColumnStart + 1)
    if (end > lineEnd) return null
    return TextRange(assertion.codeOffsetStart, end)
  }

  /** Returns the assertions whose first line starts inside [range]. */
  fun assertionsStartingIn(range: TextRange): List<PyTestAssertion> =
    parseResult.assertions.filter { range.contains(it.assertionOffsetStart) }

  /** Returns the assertion whose text holds [offset], or null. */
  fun assertionAt(offset: Int): PyTestAssertion? =
    parseResult.assertions.find { offset in it.assertionOffsetStart until it.assertionOffsetEnd }

  companion object {
    private const val PYTHON_LANGUAGE_ID = "Python"
    private val ANNOTATION = PyTestAssertionText::class.java.name

    /** The number of test methods that a string can pass through on its way to a parameter with [PyTestAssertionText]. */
    private const val FORWARDING_DEPTH = 2

    /** Returns the test text of [file], or null when [file] is not a Python text with assertions in the monorepo. */
    fun of(file: PsiFile): PyTestCaseText? {
      if (file.language.id != PYTHON_LANGUAGE_ID) return null
      return CachedValuesManager.getCachedValue(file) {
        val testText = if (isAssertionText(file)) PyTestCaseText(file.text) else null
        CachedValueProvider.Result.create(testText, PsiModificationTracker.MODIFICATION_COUNT)
      }
    }

    private fun isAssertionText(file: PsiFile): Boolean {
      val project = file.project
      if (!IntelliJProjectUtil.isIntelliJPlatformProject(project)) return false
      val host = InjectedLanguageManager.getInstance(project).getInjectionHost(file) ?: return false
      var argument = host.toUElementOfType<UExpression>() ?: return false
      while (true) {
        val parent = argument.uastParent
        argument = when {
          parent is UParenthesizedExpression -> parent
          // A call on the string, for example `"""...""".trimIndent()`.
          parent is UQualifiedReferenceExpression && parent.receiver.sourcePsi == argument.sourcePsi -> parent
          else -> break
        }
      }
      val call = argument.uastParent as? UCallExpression ?: return false
      return isAssertionArgument(call, argument, FORWARDING_DEPTH)
    }

    /** Returns true when [argument] of [call] goes to a parameter with [PyTestAssertionText], directly or through [depth] test methods. */
    private fun isAssertionArgument(call: UCallExpression, argument: UExpression, depth: Int): Boolean {
      val method = call.resolve() ?: return false
      val parameters = method.parameterList.parameters
      val index = parameters.indices.firstOrNull { call.getArgumentForParameter(it)?.sourcePsi == argument.sourcePsi } ?: return false
      val parameter = parameters[index]
      if (parameter.hasAnnotation(ANNOTATION)) return true
      return depth > 0 && forwardsParameter(method, parameter.name, depth - 1)
    }

    /** Returns true when [method] gives its parameter [parameterName] to a parameter with [PyTestAssertionText]. */
    private fun forwardsParameter(method: PsiMethod, parameterName: String, depth: Int): Boolean {
      val body = method.toUElementOfType<UMethod>()?.uastBody ?: return false
      var found = false
      body.accept(object : AbstractUastVisitor() {
        override fun visitCallExpression(node: UCallExpression): Boolean {
          if (!found) found = node.valueArguments.any { isReferenceTo(it, parameterName) && isAssertionArgument(node, it, depth) }
          return found
        }
      })
      return found
    }

    private fun isReferenceTo(argument: UExpression, parameterName: String): Boolean {
      val reference = (argument as? UQualifiedReferenceExpression)?.receiver ?: argument
      return (reference as? USimpleNameReferenceExpression)?.identifier == parameterName
    }
  }
}
