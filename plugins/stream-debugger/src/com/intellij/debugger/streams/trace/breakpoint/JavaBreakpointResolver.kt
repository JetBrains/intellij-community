// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.breakpoint

import com.intellij.debugger.streams.trace.breakpoint.ex.BreakpointPlaceNotFoundException
import com.intellij.debugger.streams.wrapper.StreamChain
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression

/**
 * @author Shumaf Lovpache
 */
class JavaBreakpointResolver(private val psiFile: PsiFile) : BreakpointResolver {
  override fun findBreakpointPlaces(chain: StreamChain): StreamChainBreakpointPlaces = ReadAction.compute<StreamChainBreakpointPlaces, Throwable> {
    val qualifierExpressionBreakpoint = findPsiMethodAt(chain.qualifierExpression.textRange)

    val intermediateCallMethods = chain.intermediateCalls
      .map { findPsiMethodAt(it.textRange) ?: throw BreakpointPlaceNotFoundException(it.name) }

    val terminationCallMethod = findPsiMethodAt(chain.terminationCall.textRange)
                                ?: throw throw BreakpointPlaceNotFoundException(chain.terminationCall.name)

    return@compute StreamChainBreakpointPlaces(qualifierExpressionBreakpoint, intermediateCallMethods, terminationCallMethod)
  }

  private fun findPsiMethodAt(stepPosition: TextRange): MethodSignature? {
    val methodCallExpression = psiFile.findElementAt(stepPosition.endOffset)?.prevSibling
                                 as? PsiMethodCallExpression ?: return null
    val psiMethod = methodCallExpression.methodExpression.reference?.resolve()
                      as? PsiMethod ?: return null

    return MethodSignature.of(psiMethod)
  }
}