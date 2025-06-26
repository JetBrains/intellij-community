// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.searching.inheritors

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.search.searches.OverridingMethodsSearch
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.mappingNotNull
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolModality
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.idea.base.analysis.KotlinUastOutOfCodeBlockModificationTracker
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

@RequiresBackgroundThread(generateAssertion = false)
fun KtCallableDeclaration.hasAnyOverridings(): Boolean =
    CachedValuesManager.getCachedValue(this) {
        val hasAnyInheritors = findAllOverridings().firstOrNull() != null
        CachedValueProvider.Result.create(
            hasAnyInheritors,
            KotlinUastOutOfCodeBlockModificationTracker.getInstance(project)
        )
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
                    val isFinal = runReadAction {
                        analyze(currentMethod) {
                            val symbol = currentMethod.symbol
                            ((symbol as? KaValueParameterSymbol)?.generatedPrimaryConstructorProperty ?: symbol).modality == KaSymbolModality.FINAL
                        }
                    }
                    if (isFinal) continue
                    DirectKotlinOverridingCallableSearch.search(currentMethod, searchScope).asIterable().forEach {
                        queue.offer(it)
                    }
                    if (withFullHierarchy) {
                        analyze(currentMethod) {
                            val ktCallableSymbol = currentMethod.symbol as? KaCallableSymbol ?: return@analyze
                            ktCallableSymbol.directlyOverriddenSymbols
                                .mapNotNull { it.psi }
                                .forEach { queue.offer(it) }
                        }
                    }
                }

                is PsiMethod -> {
                    OverridingMethodsSearch.search(currentMethod, searchScope,true)
                        .mappingNotNull { it.unwrapped }
                        .asIterable()
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
                    val isFinal = runReadAction {
                        !currentClass.isEnum() && analyze(currentClass) { // allow searching for enum constants in enum but don't bother about other final classes
                            currentClass.symbol.modality == KaSymbolModality.FINAL
                        }
                    }
                    if (isFinal) continue
                    DirectKotlinClassInheritorsSearch.search(currentClass, searchScope).asIterable().forEach {
                        queue.offer(it)
                    }
                }

                is PsiClass -> {
                    ClassInheritorsSearch.search(currentClass, searchScope, /* checkDeep = */ false)
                        .mappingNotNull { it.unwrapped }
                        .asIterable()
                        .forEach {
                            queue.offer(it)
                        }
                }
            }
        }
    }
}

@RequiresBackgroundThread(generateAssertion = false)
fun KtClass.hasAnyInheritors(): Boolean =
    CachedValuesManager.getCachedValue(this) {
        val hasAnyInheritors = findAllInheritors().firstOrNull() != null
        CachedValueProvider.Result.create(
            hasAnyInheritors,
            KotlinUastOutOfCodeBlockModificationTracker.getInstance(project)
        )
    }