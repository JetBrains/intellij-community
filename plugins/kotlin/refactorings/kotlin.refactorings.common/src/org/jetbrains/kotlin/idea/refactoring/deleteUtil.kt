// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import org.jetbrains.kotlin.lexer.KtTokens


fun deleteBracesAroundEmptyList(element: PsiElement?) {
    val nextSibling = PsiTreeUtil.skipWhitespacesAndCommentsForward(element)
    val prevSibling = PsiTreeUtil.skipWhitespacesAndCommentsBackward(element)
    if (nextSibling != null && nextSibling.elementType == KtTokens.GT &&
        prevSibling != null && prevSibling.elementType == KtTokens.LT) {
         //keep comments
        nextSibling.delete()
        prevSibling.delete()
    }
}


fun deleteSeparatingComma(e: PsiElement?) {
    val nextSibling = PsiTreeUtil.skipWhitespacesAndCommentsForward(e)
    if (nextSibling != null && nextSibling.elementType == KtTokens.COMMA) {
        nextSibling.delete()
    } else {
        val prevSibling = PsiTreeUtil.skipWhitespacesAndCommentsBackward(e)
        if (prevSibling != null && prevSibling.elementType == KtTokens.COMMA) {
            prevSibling.delete()
        }
    }
}