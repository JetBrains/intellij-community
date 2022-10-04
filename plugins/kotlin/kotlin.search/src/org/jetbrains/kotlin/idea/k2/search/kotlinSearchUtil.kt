// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.search

import com.intellij.psi.PsiMethod
import com.intellij.psi.search.searches.OverridingMethodsSearch
import com.intellij.util.AbstractQuery
import com.intellij.util.EmptyQuery
import com.intellij.util.Processor
import com.intellij.util.Query
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.psi.KtCallableDeclaration

fun search(function: KtCallableDeclaration): Query<PsiMethod> {
    val methods = function.toLightMethods()
    if (methods.isEmpty()) return EmptyQuery.getEmptyQuery()

    val queries = methods.map { OverridingMethodsSearch.search(it, true) }.toList()
    return object : AbstractQuery<PsiMethod>() {
        override fun processResults(consumer: Processor<in PsiMethod>): Boolean {
            return queries.all { it.forEach(consumer) }
        }
    }
}