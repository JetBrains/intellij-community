// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move

import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtDeclarationContainer

internal val KtDeclarationContainer.allDeclarations: List<KtDeclaration> get() = declarations.flatMap { decl ->
    if (decl is KtDeclarationContainer) listOf(decl) + decl.allDeclarations else listOf(decl)
}