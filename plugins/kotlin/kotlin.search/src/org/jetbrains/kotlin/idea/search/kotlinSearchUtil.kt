// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.search

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.search.searches.OverridingMethodsSearch
import com.intellij.util.EmptyQuery
import com.intellij.util.Query
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtClass

fun search(aClass: KtClass): Query<PsiClass> {
    val lightClass = aClass.toLightClass() ?: return EmptyQuery.getEmptyQuery()
    return ClassInheritorsSearch.search(lightClass, true)
}

fun search(function: KtCallableDeclaration): Query<PsiMethod> {
    val methods = function.toLightMethods()
    if (methods.size != 1) return EmptyQuery.getEmptyQuery()
    return OverridingMethodsSearch.search(methods[0], true)
}