// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.trace.breakpoint

import com.intellij.debugger.streams.core.wrapper.StreamChain
import com.intellij.openapi.application.readAction
import com.intellij.openapi.util.TextRange
import com.intellij.psi.JavaRecursiveElementVisitor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.util.PsiUtilCore

internal class JavaBreakpointPositionResolver : BreakpointPositionResolver {
  /**
   * Searches breakpoint places for [StreamChain] operations
   */
  override suspend fun findBreakpointPositions(chain: StreamChain): BreakpointResolveResult = readAction {
    val containingFile = chain.context.containingFile
    // qualifierExpression can be an arbitrary complex expression, for ex.
    // `a < Random.nextInt() ? Stream.of(1, 2) : SOME_API_CALL_THAT_RETURNS_STREAM()`
    // in this case we can't determine the exact place where we should put a breakpoint,
    // but in most common cases like `Stream.of(...)` it works.
    val qualifierMethod = findPsiMethodAt(containingFile, chain.qualifierExpression.textRange)
    val terminationMethod = findPsiMethodAt(containingFile, chain.terminationCall.textRange)
                            ?: return@readAction BreakpointResolveResult.NotFound

    val intermediateMethods = buildList {
      for (intermediateCall in chain.intermediateCalls) {
        val resolveResult = findPsiMethodAt(containingFile, intermediateCall.textRange)
        if (resolveResult == null) {
          return@readAction BreakpointResolveResult.NotFound
        }
        add(resolveResult)
      }
    }

    // How many calls to the same method as `qualifierMethod`
    // appear before the qualifier expression in the containing method body.
    val qualifierSkipCount = if (qualifierMethod != null) {
      val psiManager = containingFile.manager
      val qualifierEndOffset = chain.qualifierExpression.textRange.endOffset
      val containingStatement = findPsiMethodCall(containingFile, chain.terminationCall.textRange)
      var count = 0
      containingStatement?.accept(object : JavaRecursiveElementVisitor() {
        override fun visitMethodCallExpression(expr: PsiMethodCallExpression) {
          super.visitMethodCallExpression(expr)
          if (expr.textRange.endOffset < qualifierEndOffset) {
            val resolved = expr.methodExpression.resolve() as? PsiMethod
            if (resolved != null && psiManager.areElementsEquivalent(resolved, qualifierMethod)) count++
          }
        }
      })
      count
    } else {
      0
    }

    BreakpointResolveResult.Found(
      skipCount = qualifierSkipCount,
      qualifierExpressionMethod = qualifierMethod?.let { JvmMethodSignature.of(it) },
      intermediateStepsMethods = intermediateMethods.map { JvmMethodSignature.of(it) },
      terminationOperationMethod = JvmMethodSignature.of(terminationMethod),
    )
  }

  /**
   * Walk up the tree to find a `PsiMethodCallExpression` that matches this position
   *
   * Consider an example:
   * ```java
   * Stream.of(3, 4).map(x -> x + 1).count()
   * ```
   * Roughly it is represented as the following tree
   * ```
   * - PsiMethodCallExpression:Stream.of(3, 4).map(x -> x + 1).count() [range: [0-39), TERMINAL OPERATION]
   *   - PsiReferenceExpression:Stream.of(3, 4).map(x -> x + 1).count
   *     - PsiMethodCallExpression:Stream.of(3, 4).map(x -> x + 1) [range: [0-31), INTERMEDIATE OPERATION]
   *       - PsiReferenceExpression:Stream.of(3, 4).map
   *         - PsiMethodCallExpression:Stream.of(3, 4) [range: [0-15), QUALIFIER EXPRESSION]
   *           - PsiReferenceExpression:Stream.of
   *            - PsiReferenceExpression:Stream
   *              - PsiIdentifier:Stream
   *           - PsiExpressionList
   *         - PsiIdentifier:map
   *       - PsiExpressionList
   *     - PsiIdentifier:count
   *   - PsiExpressionList
   *    - PsiJavaToken:LPARENTH
   *    - PsiJavaToken:RPARENTH
   * ```
   *
   * And as we can see, each PsiMethodCallExpression that represents an operation have the same `startOffset` because calls are chained.
   *
   * As a starting point we select deepest (leaf) PSI element before the end offset.
   * In the example above it is `RPARENTH` token for each operator which is an indirect child of `PsiMethodCallExpression`
   */
  private fun findPsiMethodAt(psiFile: PsiFile, position: TextRange): PsiMethod? {
    val methodCall = findPsiMethodCall(psiFile, position) ?: return null
    return methodCall.resolveMethod()
  }

  private fun findPsiMethodCall(psiFile: PsiFile, position: TextRange): PsiMethodCallExpression? {
    val elementAt = PsiUtilCore.getElementAtOffset(psiFile, position.endOffset - 1)

    var element: PsiElement? = elementAt
    while (element != null) {
      if (element is PsiMethodCallExpression && element.textRange == position) {
        return element
      }
      element = element.parent
    }

    return null
  }
}