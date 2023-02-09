// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.testing

import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.util.ThreeState
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.types.TypeEvalContext

class PyTestLineMarkerContributor : RunLineMarkerContributor() {
  override fun getInfo(element: PsiElement): Info? {
    if ((element !is LeafPsiElement) || element.elementType != PyTokenTypes.IDENTIFIER) {
      return null
    }
    val testElement = element.parent ?: return null

    val typeEvalContext = TypeEvalContext.codeAnalysis(element.project, element.containingFile)
    if ((testElement is PyClass || testElement is PyFunction)
        && isTestElement(testElement, ThreeState.UNSURE, typeEvalContext)) {
      return withExecutorActions(AllIcons.RunConfigurations.TestState.Run)
    }
    return null
  }
}
