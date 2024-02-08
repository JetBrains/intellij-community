// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.huggingFace.documentation

import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.PsiDocumentationTargetProvider
import com.intellij.psi.PsiElement
import com.intellij.python.community.impl.huggingFace.HuggingFaceUtil
import com.intellij.python.community.impl.huggingFace.annotation.HuggingFaceDatasetPsiElement
import com.intellij.python.community.impl.huggingFace.annotation.HuggingFaceModelPsiElement
import com.jetbrains.python.psi.PyStringLiteralExpression
import com.jetbrains.python.psi.PyTargetExpression


class HuggingFaceDocumentationTargetProvider : PsiDocumentationTargetProvider {
  override fun documentationTargets(element: PsiElement, originalElement: PsiElement?): List<DocumentationTarget> {
    val documentationTargets = mutableListOf<DocumentationTarget>()

    if (element is PyTargetExpression) {
      val referencedElement = element.findAssignedValue()

      if (referencedElement is PyStringLiteralExpression) {
        val stringValue = referencedElement.stringValue
        if (HuggingFaceUtil.isHuggingFaceModel(stringValue) || HuggingFaceUtil.isHuggingFaceDataset(stringValue)) {
          documentationTargets.add(HuggingFaceDocumentationTarget(element))
        }
      }
    } else if (element is HuggingFaceModelPsiElement) {
      documentationTargets.add(HuggingFaceDocumentationTarget(element))
    } else if (element is HuggingFaceDatasetPsiElement) {
      documentationTargets.add(HuggingFaceDocumentationTarget(element))
    }

    return documentationTargets
  }

  override fun documentationTarget(element: PsiElement, originalElement: PsiElement?): DocumentationTarget? {
    val targets = documentationTargets(element, originalElement)
    return targets.firstOrNull()
  }
}