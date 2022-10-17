// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.inheritorsSearch

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.searches.OverridingMethodsSearch
import com.intellij.util.mappingNotNull
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import java.util.*

/**
 * Returns a set of PsiMethods/KtFunctions/KtProperties which "deep" override current [function].
 */
fun findAllOverridings(function: KtCallableDeclaration): Set<PsiElement> {
    return findAllOverridings(function, false)
}

/**
 * Returns a set of PsiMethods/KtFunctions/KtProperties which belong to the hierarchy of [function].
 * 
 * Example:
 * ```
 * interface A { fun f() }
 * interface B { fun f() }
 * interface C : A, B { override fun f() }
 * ```
 * Hierarchy for B.f contains both A.f and C.f
 */
fun findFullHierarchy(function: KtCallableDeclaration) : Set<PsiElement> {
    return findAllOverridings(function, true)
} 

private fun findAllOverridings(function: KtCallableDeclaration, withFullHierarchy : Boolean): Set<PsiElement> {
    val queue = ArrayDeque<PsiElement>()
    val visited = HashSet<PsiElement>()

    queue.add(function)
    while (!queue.isEmpty()) {
        val currentMethod = queue.poll()
        visited += currentMethod
        if (currentMethod is KtCallableDeclaration) {
            DirectKotlinOverridingCallableSearch.search(currentMethod).forEach {
                queue.offer(it)
            }
            if (withFullHierarchy) {
                analyze(currentMethod) {
                    val ktCallableSymbol = currentMethod.getSymbol() as? KtCallableSymbol ?: return@analyze
                    ktCallableSymbol.getDirectlyOverriddenSymbols()
                        .mapNotNull {
                            it.psi
                        }
                        .filter { it !in visited }
                        .forEach { queue.offer(it) }
                }
            }
        }
        else if (currentMethod is PsiMethod) {
            OverridingMethodsSearch.search(currentMethod, true)
                .mappingNotNull { it.unwrapped }
                .filter { it !in visited }
                .forEach { queue.offer(it) }
            if (withFullHierarchy) {
                currentMethod.findSuperMethods(true)
                    .mapNotNull { it.unwrapped }
                    .filter { it !in visited }
                    .forEach { queue.offer(it) }
            }
        }
    }
    return visited
}