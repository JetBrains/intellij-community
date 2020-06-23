// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.mlcompletion.prev2calls

import com.completion.features.models.prevcalls.python.PrevCallsContextInfo
import com.completion.features.models.prevcalls.python.PrevCallsModelResponse
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFunction

object PyPrevCallsCompletionFeatures {
  val PREV_CALLS_CONTEXT_INFO_KEY = Key<PrevCallsContextInfo>("py.ml.completion.prev.calls.user.data")

  fun calculatePrevCallsContextInfo(cursorOffset: Int, psiPosition: PsiElement, isInIf: Boolean, isInLoop: Boolean): PrevCallsContextInfo? {
    val scopePsiElement =
      PsiTreeUtil.getParentOfType(psiPosition, PyFunction::class.java, PyClass::class.java, PyFile::class.java) ?: return null

    val importsVisitor = ImportsVisitor()
    psiPosition.containingFile.accept(importsVisitor)
    val assignmentVisitor = AssignmentVisitor(cursorOffset, scopePsiElement, importsVisitor.fullNames)

    scopePsiElement.accept(assignmentVisitor)

    val allCalls = assignmentVisitor.arrPrevCalls.asReversed()
    if (allCalls.isEmpty()) return null

    val qualifierName = allCalls[0].qualifier
    val previousCalls = allCalls
      .asSequence()
      .drop(1)
      .filter { it.qualifier == qualifierName }
      .map { it.reference }
      .toList()

    return PrevCallsContextInfo(previousCalls, qualifierName, isInIf, isInLoop)
  }

  fun getResult(lookupString: String, contextInfo: PrevCallsContextInfo): PrevCallsModelResponse? {
    val model = PrevCallsModelsProviderService.instance.getModelFor(contextInfo.qualifier.substringBefore("."))
                ?: return null
    return model.getWeightForElement(lookupString, contextInfo)
  }
}