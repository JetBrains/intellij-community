// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.psi.impl

import com.intellij.debugger.streams.psi.ChainDetector
import com.intellij.debugger.streams.psi.StreamApiUtil
import com.intellij.psi.PsiMethodCallExpression

/**
 * @author Vitaliy.Bibaev
 */
class JavaStreamChainDetector : ChainDetector {
  override fun isTerminationCall(callExpression: PsiMethodCallExpression): Boolean =
    StreamApiUtil.isTerminationStreamCall(callExpression)

  override fun isIntermediateCall(callExpression: PsiMethodCallExpression): Boolean =
    StreamApiUtil.isIntermediateStreamCall(callExpression)
}