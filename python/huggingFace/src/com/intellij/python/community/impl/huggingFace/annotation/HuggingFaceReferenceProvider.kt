// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.huggingFace.annotation

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceProvider
import com.intellij.python.community.impl.huggingFace.HuggingFaceUtil
import com.intellij.python.community.impl.huggingFace.service.HuggingFacePluginManager
import com.intellij.util.ProcessingContext
import com.jetbrains.python.psi.PyStringLiteralExpression
import org.jetbrains.annotations.ApiStatus


@ApiStatus.Internal
class HuggingFaceIdentifierReferenceProvider : PsiReferenceProvider() {
  override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
    val pyStringLiteralExpression = element as? PyStringLiteralExpression ?: return PsiReference.EMPTY_ARRAY
    val text = pyStringLiteralExpression.stringValue
    if (!HF_WORTHY_STRING_REGEX.matches(text)) return PsiReference.EMPTY_ARRAY

    val project = element.project
    val pluginManager = project.getService(HuggingFacePluginManager::class.java)
    if (!pluginManager.isActive()) return PsiReference.EMPTY_ARRAY

    val entityKind = HuggingFaceUtil.isWhatHuggingFaceEntity(text) ?: return PsiReference.EMPTY_ARRAY

    val textRange = getTextRange(element, text)
    return arrayOf(HuggingFaceReference(element, textRange, text, entityKind))
  }

  private fun getTextRange(element: PsiElement, text: String): TextRange {
    val startOffset = element.text.indexOf(text)
    return if (startOffset >= 0) {
      TextRange(startOffset, startOffset + text.length)
    } else {
      TextRange.EMPTY_RANGE
    }
  }

  companion object {
    private val HF_WORTHY_STRING_REGEX = Regex("""[\w.-]+/[\w.-]+""")
  }
}
