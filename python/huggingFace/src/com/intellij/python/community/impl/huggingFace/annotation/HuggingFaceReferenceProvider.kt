// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.huggingFace.annotation

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceProvider
import com.intellij.python.community.impl.huggingFace.service.HuggingFaceImportedLibrariesManagerService
import com.intellij.python.community.impl.huggingFace.HuggingFaceUtil
import com.intellij.util.ProcessingContext
import com.jetbrains.python.psi.PyStringLiteralExpression

abstract class HuggingFaceReferenceProvider : PsiReferenceProvider() {

  override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
    val pyStringLiteralExpression = element as? PyStringLiteralExpression ?: return PsiReference.EMPTY_ARRAY

    val project = element.project
    val service = project.getService(HuggingFaceImportedLibrariesManagerService::class.java)
    val manager = service.getManager()
    if (!manager.isLibraryImported()) return PsiReference.EMPTY_ARRAY

    val text = pyStringLiteralExpression.stringValue
    if (!isValidReference(text)) return PsiReference.EMPTY_ARRAY

    val textRange = getTextRange(element, text)
    return getReferenceArray(element, textRange, text)
  }

  abstract fun isValidReference(text: String) : Boolean

  abstract fun getReferenceArray(element: PsiElement, textRange: TextRange, text: String) : Array<PsiReference>

  private fun getTextRange(element: PsiElement, text: String): TextRange {
    val startOffset = element.text.indexOf(text)
    return if (startOffset >= 0) {
      TextRange(startOffset, startOffset + text.length)
    } else {
      TextRange.EMPTY_RANGE
    }
  }
}

class HuggingFaceModelReferenceProvider : HuggingFaceReferenceProvider() {
  override fun isValidReference(text: String) = HuggingFaceUtil.isHuggingFaceModel(text)

  override fun getReferenceArray(element: PsiElement, textRange: TextRange, text: String): Array<PsiReference> =
    arrayOf(HuggingFaceModelReference(element, textRange, text))
}

class HuggingFaceDatasetReferenceProvider : HuggingFaceReferenceProvider() {
  override fun isValidReference(text: String) = HuggingFaceUtil.isHuggingFaceDataset(text)

  override fun getReferenceArray(element: PsiElement, textRange: TextRange, text: String): Array<PsiReference> =
    arrayOf(HuggingFaceDatasetReference(element, textRange, text))
}
