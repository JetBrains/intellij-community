// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.mlcompletion

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyArgumentList
import com.jetbrains.python.psi.PyKeywordArgument
import com.jetbrains.python.psi.PyReferenceExpression

object PyArgumentsCompletionFeatures {
  data class ArgumentsContextCompletionFeatures(
    val isInArguments: Boolean,
    val isDirectlyInArgumentContext: Boolean,
    val isIntoKeywordArgument: Boolean,
    val argumentIndex: Int?,
    val argumentsSize: Int?,
    val haveNamedArgLeft: Boolean,
    val haveNamedArgRight: Boolean)

  fun getContextArgumentFeatures(locationPsi: PsiElement): ArgumentsContextCompletionFeatures {
    val argListParent = PsiTreeUtil.getParentOfType(locationPsi, PyArgumentList::class.java)
    val isInArguments = argListParent != null
    val isDirectlyInArgumentContext = isDirectlyInArgumentsContext(locationPsi)
    val isIntoKeywordArgument = PsiTreeUtil.getParentOfType(locationPsi, PyKeywordArgument::class.java) != null

    var argumentIndex: Int? = null
    var argumentsSize: Int? = null
    var haveNamedArgLeft = false
    var haveNamedArgRight = false

    if (argListParent != null) {
      val arguments = argListParent.arguments
      argumentsSize = arguments.size
      for (i in arguments.indices) {
        if (PsiTreeUtil.isAncestor(arguments[i], locationPsi, false)) {
          argumentIndex = i
        }
        else if (arguments[i] is PyKeywordArgument) {
          if (argumentIndex == null) {
            haveNamedArgLeft = true
          }
          else {
            haveNamedArgRight = true
          }
        }
      }
    }

    return ArgumentsContextCompletionFeatures(isInArguments,
                                              isDirectlyInArgumentContext,
                                              isIntoKeywordArgument,
                                              argumentIndex,
                                              argumentsSize,
                                              haveNamedArgLeft,
                                              haveNamedArgRight)
  }

  private fun isDirectlyInArgumentsContext(locationPsi: PsiElement): Boolean {
    // for zero prefix
    if (locationPsi.parent is PyArgumentList) return true

    // for non-zero prefix
    if (locationPsi.parent !is PyReferenceExpression) return false
    if (locationPsi.parent.parent !is PyArgumentList) return false

    return true
  }
}