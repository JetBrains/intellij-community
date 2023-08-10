// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.rename

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil

inline fun <reified T : PsiElement> PsiFile.findElementForRename(offset: Int): T? {
    return PsiTreeUtil.findElementOfClassAtOffset(this, offset, T::class.java, false)
        ?: PsiTreeUtil.findElementOfClassAtOffset(this, (offset - 1).coerceAtLeast(0), T::class.java, false)
}