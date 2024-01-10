// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.introduce

import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.patternMatching.AbstractKotlinPsiUnifierTest

abstract class AbstractK2PsiUnifierTest : AbstractKotlinPsiUnifierTest() {
    override fun isFirPlugin(): Boolean = true

    override fun KtElement.getMatches(file: KtFile): List<String> = emptyList()
}