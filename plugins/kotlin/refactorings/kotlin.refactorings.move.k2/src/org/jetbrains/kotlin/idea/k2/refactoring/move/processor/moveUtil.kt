// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.processor

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveSourceDescriptor
import org.jetbrains.kotlin.psi.KtFile

fun K2MoveSourceDescriptor.ElementSource.moveInto(targetFile: KtFile): Map<PsiElement, PsiElement> {
    return elements.associateWith { declaration -> targetFile.add(declaration) }
}