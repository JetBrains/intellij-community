// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.j2k

import com.intellij.psi.javadoc.PsiDocComment
import org.jetbrains.kotlin.idea.j2k.DocCommentConverter

object EmptyDocCommentConverter : DocCommentConverter {
    override fun convertDocComment(docComment: PsiDocComment): String = docComment.text
}