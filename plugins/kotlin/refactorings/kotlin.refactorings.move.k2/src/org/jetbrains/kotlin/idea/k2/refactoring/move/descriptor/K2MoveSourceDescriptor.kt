// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor

import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration

sealed interface K2MoveSourceDescriptor<T : KtElement> {
    val elements: Set<T>

    class FileSource(override val elements: Set<KtFile>) : K2MoveSourceDescriptor<KtFile>

    class ElementSource(override val elements: Set<KtNamedDeclaration>) : K2MoveSourceDescriptor<KtNamedDeclaration>
}