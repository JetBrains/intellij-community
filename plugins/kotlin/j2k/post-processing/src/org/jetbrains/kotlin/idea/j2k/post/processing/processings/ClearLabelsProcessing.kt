// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.j2k.post.processing.processings

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.idea.j2k.post.processing.GeneralPostProcessing
import org.jetbrains.kotlin.idea.j2k.post.processing.elements
import org.jetbrains.kotlin.idea.j2k.post.processing.runUndoTransparentActionInEdt
import org.jetbrains.kotlin.j2k.JKPostProcessingTarget
import org.jetbrains.kotlin.nj2k.NewJ2kConverterContext
import org.jetbrains.kotlin.nj2k.asExplicitLabel
import org.jetbrains.kotlin.nj2k.asInferenceLabel

internal class ClearUnknownInferenceLabelsProcessing : GeneralPostProcessing {
    override fun runProcessing(target: JKPostProcessingTarget, converterContext: NewJ2kConverterContext) {
        target.deleteLabelComments { comment -> comment.text.asInferenceLabel() != null }
    }
}

internal class ClearExplicitLabelsProcessing : GeneralPostProcessing {
    override fun runProcessing(target: JKPostProcessingTarget, converterContext: NewJ2kConverterContext) {
        target.deleteLabelComments { comment -> comment.text.asExplicitLabel() != null }
    }
}

private fun JKPostProcessingTarget.deleteLabelComments(filter: (PsiComment) -> Boolean) {
    val comments = mutableListOf<PsiComment>()
    for (element in elements()) {
        element.accept(object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                element.acceptChildren(this)
            }

            override fun visitComment(comment: PsiComment) {
                if (runReadAction { filter(comment) }) {
                    comments += comment
                }
            }
        })
    }

    runUndoTransparentActionInEdt(inWriteAction = true) {
        comments.forEach { it.delete() }
    }
}
