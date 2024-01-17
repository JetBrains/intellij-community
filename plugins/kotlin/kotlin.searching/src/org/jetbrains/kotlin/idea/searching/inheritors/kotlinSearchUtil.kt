// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.searching.inheritors

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.search.searches.OverridingMethodsSearch
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.mappingNotNull
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import java.util.*

/**
 * Returns a set of PsiMethods/KtFunctions/KtProperties which "deep" override current [this] declaration.
 *
 * Example:
 * ```
 * interface A { fun f() }
 * interface B { fun f() }
 * interface C : A, B { override fun f() }
 * ```
 * For B.f, this method returns **only** C.f
 */
@RequiresBackgroundThread(generateAssertion = false)
fun KtCallableDeclaration.findAllOverridings(searchScope: SearchScope = runReadAction { useScope }): Sequence<PsiElement> {
    return findAllOverridings(withFullHierarchy = false, searchScope)
}

/**
 * Returns a set of PsiMethods/KtFunctions/KtProperties which belong to the hierarchy of a function and all its siblings.
 *
 * Example:
 * ```
 * interface A { fun f() }
 * interface B { fun f() }
 * interface C : A, B { override fun f() }
 * ```
 * Hierarchy for B.f contains both A.f and C.f
 */
@RequiresBackgroundThread(generateAssertion = false)
fun KtCallableDeclaration.findHierarchyWithSiblings(searchScope: SearchScope = runReadAction {useScope}) : Sequence<PsiElement> {
    return findAllOverridings(withFullHierarchy = true, searchScope)
} 

private fun KtCallableDeclaration.findAllOverridings(withFullHierarchy: Boolean, searchScope: SearchScope): Sequence<PsiElement> {
    if (runReadAction { containingClassOrObject !is KtClass }) return emptySequence()

    val queue = ArrayDeque<PsiElement>()
    val visited = HashSet<PsiElement>()

    queue.add(this)
    return sequence {
        while (!queue.isEmpty()) {
            val currentMethod = queue.poll()
            if (!visited.add(currentMethod)) continue
            if (this@findAllOverridings != currentMethod) {
                yield(currentMethod)
            }
            when (currentMethod) {
                is KtCallableDeclaration -> {
                    DirectKotlinOverridingCallableSearch.search(currentMethod, searchScope).forEach {
                        queue.offer(it)
                    }
                    if (withFullHierarchy) {
                        analyze(currentMethod) {
                            val ktCallableSymbol = currentMethod.getSymbol() as? KtCallableSymbol ?: return@analyze
                            ktCallableSymbol.getDirectlyOverriddenSymbols()
                                .mapNotNull { it.psi }
                                .forEach { queue.offer(it) }
                        }
                    }
                }

                is PsiMethod -> {
                    OverridingMethodsSearch.search(currentMethod, searchScope,true)
                        .mappingNotNull { it.unwrapped }
                        .forEach { queue.offer(it) }
                    if (withFullHierarchy) {
                        currentMethod.findSuperMethods(true)
                            .mapNotNull { it.unwrapped }
                            .forEach { queue.offer(it) }
                    }
                }
            }
        }
    }
}

/**
 * Finds all the classes inheriting from [this] class. The resulting set does not
 * include the class itself.
 *
 * Example:
 * ```
 * interface A
 * interface B : A
 * interface C : B
 * interface D : C
 * ```
 *
 * Calling this function for class `B` will return classes `C` and `D`.
 */
@RequiresBackgroundThread(generateAssertion = false)
fun KtClass.findAllInheritors(searchScope: SearchScope = useScope): Sequence<PsiElement> {
    val queue = ArrayDeque<PsiElement>()
    val visited = HashSet<PsiElement>()

    queue.add(this)
    return sequence {
        while (!queue.isEmpty()) {
            val currentClass = queue.poll()
            if (!visited.add(currentClass)) continue
            if (currentClass != this@findAllInheritors) {
                yield(currentClass)
            }
            when (currentClass) {
                is KtClass -> {
                    DirectKotlinClassInheritorsSearch.search(currentClass, searchScope).forEach {
                        queue.offer(it)
                    }
                }

                is PsiClass -> {
                    ClassInheritorsSearch.search(currentClass, searchScope, /* checkDeep = */ false)
                        .mappingNotNull { it.unwrapped }
                        .forEach {
                            queue.offer(it)
                        }
                }
            }
        }
    }
}