/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.jetbrains.python.testing

import com.intellij.execution.TestStateStorage
import com.intellij.execution.lineMarker.ExecutorAction
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.icons.AllIcons
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.util.ThreeState
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.types.TypeEvalContext

private fun getUrlByElement(testElement: PsiNamedElement, module: Module, typeEvalContext: TypeEvalContext)
  : String? {

  val name = testElement.name ?: return null

  val testStateStorage = TestStateStorage.getInstance(testElement.project)
  return testStateStorage.keys
    .filter { name in it }
    .find { testElement == getElementByUrl(it, module, typeEvalContext)?.psiElement }
}

object PyTestLineMarkerProvider : RunLineMarkerContributor() {
  override fun getInfo(element: PsiElement): Info? {

    if ((element !is LeafPsiElement) || element.elementType != PyTokenTypes.IDENTIFIER) {
      return null
    }
    val testElement = element.parent ?: return null

    val typeEvalContext = TypeEvalContext.codeAnalysis(element.project, element.containingFile)
    if ((testElement is PyClass || testElement is PyFunction)
        && (testElement is PsiNamedElement)
        && isTestElement(testElement, ThreeState.UNSURE, typeEvalContext)) {


      val module = ModuleUtil.findModuleForPsiElement(element) ?: return null


      val icon = getUrlByElement(testElement, module, typeEvalContext)?.let {
        getTestStateIcon(it, module.project, testElement is PyClass)
      } ?: AllIcons.RunConfigurations.TestState.Run


      return RunLineMarkerContributor.Info(
        icon,
        ExecutorAction.getActions(1),
        RunLineMarkerContributor.RUN_TEST_TOOLTIP_PROVIDER)
    }
    return null
  }
}