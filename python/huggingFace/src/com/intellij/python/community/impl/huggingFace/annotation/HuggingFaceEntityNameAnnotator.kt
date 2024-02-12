// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.huggingFace.annotation

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.jetbrains.python.psi.PyStringLiteralExpression
import com.intellij.python.community.impl.huggingFace.service.HuggingFaceImportedLibrariesManagerService

class HuggingFaceEntityNameAnnotator : Annotator {
  override fun annotate(element: PsiElement, holder: AnnotationHolder) {
    if (element !is PyStringLiteralExpression) return

    val userData = element.getUserData(HUGGING_FACE_ENTITY_NAME_KEY)
    if (userData == null || !userData) return

    val project = element.project
    val service = project.getService(HuggingFaceImportedLibrariesManagerService::class.java)
    val manager = service.getManager()
    if (!manager.isLibraryImported()) return

    val textRangeInElement = element.getTextRange()
    val startOffset = textRangeInElement.startOffset + 1
    val endOffset = textRangeInElement.endOffset - 1
    val modelRange = TextRange(startOffset, endOffset)

    holder.newSilentAnnotation(HighlightSeverity.TEXT_ATTRIBUTES)
      .range(modelRange)
      .textAttributes(CodeInsightColors.INACTIVE_HYPERLINK_ATTRIBUTES)
      .create()
  }
}
