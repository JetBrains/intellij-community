// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.j2k.post.processing.processings

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiRecursiveVisitor
import org.jetbrains.kotlin.idea.j2k.post.processing.runUndoTransparentActionInEdt
import org.jetbrains.kotlin.j2k.*
import org.jetbrains.kotlin.nj2k.asExplicitLabel
import org.jetbrains.kotlin.nj2k.asInferenceLabel

internal class ClearUnknownInferenceLabelsProcessing : PostProcessing {
    override fun runProcessing(target: PostProcessingTarget, converterContext: ConverterContext) {
        target.deleteLabelComments { comment -> comment.text.asInferenceLabel() != null }
    }

    override fun computeAppliers(target: PostProcessingTarget, converterContext: ConverterContext): List<PostProcessingApplier> {
        error("Not supported in K1 J2K")
    }
}

internal class ClearExplicitLabelsProcessing : PostProcessing {
    override fun runProcessing(target: PostProcessingTarget, converterContext: ConverterContext) {
        target.deleteLabelComments { comment -> comment.text.asExplicitLabel() != null }
    }

    override fun computeAppliers(target: PostProcessingTarget, converterContext: ConverterContext): List<PostProcessingApplier> {
        error("Not supported in K1 J2K")
    }
}

private fun PostProcessingTarget.deleteLabelComments(filter: (PsiComment) -> Boolean) {
    val comments = mutableListOf<PsiComment>()

    runReadAction {
        for (element in elements()) {
            element.accept(object : PsiElementVisitor(),PsiRecursiveVisitor {
                override fun visitElement(element: PsiElement) {
                    element.acceptChildren(this)
                }

                override fun visitComment(comment: PsiComment) {
                    if (filter(comment)) {
                        comments += comment
                    }
                }
            })
        }
    }

    runUndoTransparentActionInEdt(inWriteAction = true) {
        comments.forEach { it.delete() }
    }
}
