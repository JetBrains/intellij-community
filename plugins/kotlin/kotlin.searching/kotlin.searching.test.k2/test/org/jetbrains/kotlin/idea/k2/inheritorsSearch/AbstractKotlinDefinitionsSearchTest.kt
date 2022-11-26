// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.inheritorsSearch

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.search.searches.DefinitionsScopedSearch
import com.intellij.util.Query
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFunction

abstract class AbstractKotlinDefinitionsSearchTest : AbstractKotlinSearchersTest() {
    override fun searchClass(ktClass: KtClass): Query<PsiElement> {
        return DefinitionsScopedSearch.search(ktClass)
    }

    override fun searchFunction(ktFunction: KtFunction): Query<PsiElement> {
        return DefinitionsScopedSearch.search(ktFunction)
    }

    override fun searchJavaClass(psiClass: PsiClass): Query<PsiElement> {
        return DefinitionsScopedSearch.search(psiClass)
    }
}