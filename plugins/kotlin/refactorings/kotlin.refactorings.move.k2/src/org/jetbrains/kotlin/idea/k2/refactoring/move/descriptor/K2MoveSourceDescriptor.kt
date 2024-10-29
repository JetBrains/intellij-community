// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import org.jetbrains.kotlin.psi.KtNamedDeclaration

sealed interface K2MoveSourceDescriptor<T : PsiElement> {
    val elements: Collection<T>

    class FileSource(override val elements: Collection<PsiFileSystemItem>) : K2MoveSourceDescriptor<PsiFileSystemItem>

    class ElementSource(override val elements: Collection<KtNamedDeclaration>) : K2MoveSourceDescriptor<KtNamedDeclaration>
}