// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.psi.impl

import com.intellij.debugger.streams.psi.ChainDetector
import com.intellij.psi.PsiMethodCallExpression

/**
 * @author Vitaliy.Bibaev
 */
class PackageChainDetector(private val delegate: ChainDetector, private val packageName: String) : ChainDetector {
  companion object {
    fun forJavaStreams(packageName: String) = PackageChainDetector(JavaStreamChainDetector(), packageName)
  }

  override fun isTerminationCall(callExpression: PsiMethodCallExpression): Boolean =
    delegate.isTerminationCall(callExpression) && isPackageSupported(callExpression)

  override fun isIntermediateCall(callExpression: PsiMethodCallExpression): Boolean =
    delegate.isIntermediateCall(callExpression) && isPackageSupported(callExpression)

  override fun isStreamCall(callExpression: PsiMethodCallExpression): Boolean =
    delegate.isStreamCall(callExpression) && isPackageSupported(callExpression)

  private fun isPackageSupported(name: String): Boolean {
    return name.startsWith(packageName)
  }

  private fun isPackageSupported(callExpression: PsiMethodCallExpression) = isPackageSupported(extractPackage(callExpression))

  private fun extractPackage(callExpression: PsiMethodCallExpression): String {
    val psiMethod = callExpression.resolveMethod()
    if (psiMethod != null) {
      val topClass = com.intellij.psi.util.PsiUtil.getTopLevelClass(psiMethod)
      if (topClass != null) {
        val packageName = com.intellij.psi.util.PsiUtil.getPackageName(topClass)
        if (packageName != null) {
          return packageName
        }
      }
    }

    return ""
  }
}