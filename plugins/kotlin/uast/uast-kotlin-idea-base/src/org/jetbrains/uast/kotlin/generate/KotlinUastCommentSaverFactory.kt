// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.kotlin.generate

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.psi.psiUtil.PsiChildRange
import org.jetbrains.uast.UElement
import org.jetbrains.uast.generate.UastCommentSaver

@ApiStatus.Experimental
fun createUastCommentSaver(firstResultUElement: UElement, lastResultUElement: UElement): UastCommentSaver? {
    val firstSourcePsiElement = firstResultUElement.sourcePsi ?: return null
    val lastSourcePsiElement = lastResultUElement.sourcePsi ?: firstSourcePsiElement
    val commentSaver = CommentSaver(PsiChildRange(firstSourcePsiElement, lastSourcePsiElement))
    return object : UastCommentSaver {
        override fun restore(firstResultUElement: UElement, lastResultUElement: UElement) {
            val firstPsiElement = firstResultUElement.sourcePsi ?: return
            val lastPsiElement = lastResultUElement.sourcePsi ?: firstPsiElement
            commentSaver.restore(PsiChildRange(firstPsiElement, lastPsiElement))
        }

        override fun markUnchanged(firstResultUElement: UElement?, lastResultUElement: UElement?) {
            //do nothing
        }
    }
}
