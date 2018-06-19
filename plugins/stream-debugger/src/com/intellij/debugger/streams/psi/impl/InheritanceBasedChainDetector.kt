// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.psi.impl

import com.intellij.debugger.streams.psi.ChainDetector
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiType
import com.intellij.psi.util.InheritanceUtil

/**
 * @author Vitaliy.Bibaev
 */
class InheritanceBasedChainDetector(private val baseClassName: String) : ChainDetector {
  override fun isTerminationCall(callExpression: PsiMethodCallExpression): Boolean {
    val method = callExpression.resolveMethod() ?: return false
    return isStreamType(method.parent as? PsiClass) && !isStreamType(method.returnType)
  }

  override fun isIntermediateCall(callExpression: PsiMethodCallExpression): Boolean {
    val method = callExpression.resolveMethod() ?: return false
    return !method.isStatic() && isStreamType(method.parent as? PsiClass) && isStreamType(callExpression.resolveMethod()?.returnType)
  }

  private fun isStreamType(type: PsiType?): Boolean = InheritanceUtil.isInheritor(type, baseClassName)

  private fun isStreamType(type: PsiClass?): Boolean = InheritanceUtil.isInheritor(type, baseClassName)

  private fun PsiMethod.isStatic(): Boolean = this.hasModifier(JvmModifier.STATIC)
}