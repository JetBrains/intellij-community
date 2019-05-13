// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.psi

import com.intellij.psi.PsiMethodCallExpression

/**
 * @author Vitaliy.Bibaev
 */
interface ChainDetector {
  fun isTerminationCall(callExpression: PsiMethodCallExpression): Boolean
  fun isIntermediateCall(callExpression: PsiMethodCallExpression): Boolean
  fun isStreamCall(callExpression: PsiMethodCallExpression): Boolean =
    isIntermediateCall(callExpression) || isTerminationCall(callExpression)
}