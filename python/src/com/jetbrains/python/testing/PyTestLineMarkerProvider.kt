// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.testing

import com.intellij.execution.TestStateStorage
import com.intellij.execution.lineMarker.ExecutorAction
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.icons.AllIcons
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.util.ThreeState
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyQualifiedNameOwner
import com.jetbrains.python.psi.types.TypeEvalContext

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
        getTestStateIcon(it, testElement.project, testElement is PyClass)
      } ?: AllIcons.RunConfigurations.TestState.Run


      return RunLineMarkerContributor.Info(
        icon,
        ExecutorAction.getActions(1),
        RunLineMarkerContributor.RUN_TEST_TOOLTIP_PROVIDER)
    }
    return null
  }
}

/**
 * If [storage] has url created from [qname] return it.
 */
private fun getUrlByQName(qname: String, storage: TestStateStorage): String? {
  // Protocol contains folder and may be different in different runners, so we use first key to fetch it
  val firstUrl = storage.keys.firstOrNull() ?: return null
  val protocol = VirtualFileManager.extractProtocol(firstUrl) ?: return null
  val url = VirtualFileManager.constructUrl(protocol, qname)
  return if (storage.getState(url) != null) url else null
}

private fun getUrlByElement(testElement: PsiNamedElement, module: Module, typeEvalContext: TypeEvalContext)
  : String? {

  val name = testElement.name ?: return null

  val testStateStorage = TestStateStorage.getInstance(testElement.project)

  // If element's qname is enough to create its url no need to iterate through whole list
  (testElement as? PyQualifiedNameOwner)?.qualifiedName?.let { qname ->
    getUrlByQName(qname, testStateStorage)?.let { return it }
  }

  // Element has some different url, find url with element's name, resolve it and check if it matches element
  return testStateStorage.keys
    .filter { name in it }
    .find { testElement == getElementByUrl(it, module, typeEvalContext)?.psiElement }
}