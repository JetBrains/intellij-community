// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.j2k.post.processing.processings

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.idea.j2k.post.processing.GeneralPostProcessing
import org.jetbrains.kotlin.idea.j2k.post.processing.runUndoTransparentActionInEdt
import org.jetbrains.kotlin.j2k.JKPostProcessingTarget
import org.jetbrains.kotlin.j2k.elements
import org.jetbrains.kotlin.nj2k.NewJ2kConverterContext
import org.jetbrains.kotlin.nj2k.asLabel

internal class ClearUnknownLabelsProcessing : GeneralPostProcessing {
    override fun runProcessing(target: JKPostProcessingTarget, converterContext: NewJ2kConverterContext) {
        val comments = mutableListOf<PsiComment>()
        runUndoTransparentActionInEdt(inWriteAction = true) {
            target.elements().forEach { element ->
                element.accept(object : PsiElementVisitor() {
                    override fun visitElement(element: PsiElement) {
                        element.acceptChildren(this)
                    }

                    override fun visitComment(comment: PsiComment) {
                        if (comment.text.asLabel() != null) {
                            comments += comment
                        }
                    }
                })
            }
            comments.forEach { it.delete() }
        }
    }
}