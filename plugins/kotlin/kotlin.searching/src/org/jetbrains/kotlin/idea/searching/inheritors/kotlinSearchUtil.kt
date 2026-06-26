// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.searching.inheritors

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.search.searches.OverridingMethodsSearch
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.util.CommonProcessors
import com.intellij.util.Processor
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.mappingNotNull
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolModality
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.idea.base.analysis.KotlinUastOutOfCodeBlockModificationTracker
import org.jetbrains.kotlin.idea.searching.kmp.findAllActualForExpect
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicInteger

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
fun KtCallableDeclaration.hasAnyOverridings(): Boolean {
    fun hasFound(): Boolean {
        val findProcessor = CommonProcessors.FindFirstProcessor<PsiElement>()
        processAllOverridings(findProcessor)
        val value = findProcessor.isFound
        return value
    }

    if (!isPhysical) return hasFound()
    return CachedValuesManager.getCachedValue(this) {
        CachedValueProvider.Result.create(
            hasFound(),
            KotlinUastOutOfCodeBlockModificationTracker.getInstance(project)
        )
    }
}

@RequiresBackgroundThread(generateAssertion = false)
fun KtCallableDeclaration.hasAnyActuals(): Boolean =
    CachedValuesManager.getCachedValue(this) {
        val hasAnyActuals = findAllActualForExpect().firstOrNull() != null
        CachedValueProvider.Result.create(
            hasAnyActuals,
            KotlinUastOutOfCodeBlockModificationTracker.getInstance(project)
        )
    }

@RequiresBackgroundThread(generateAssertion = false)
fun KtClass.hasAnyActuals(): Boolean =
    CachedValuesManager.getCachedValue(this) {
        val hasAnyActuals = findAllActualForExpect().firstOrNull() != null
        CachedValueProvider.Result.create(
            hasAnyActuals,
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
    if (runReadActionBlocking { containingClassOrObject !is KtClass }) return emptySequence()

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
                    val isFinal = runReadActionBlocking {
                        analyze(currentMethod) {
                            val symbol = currentMethod.symbol
                            ((symbol as? KaValueParameterSymbol)?.generatedPrimaryConstructorProperty ?: symbol).modality == KaSymbolModality.FINAL
                        }
                    }
                    if (isFinal) continue
                    DirectKotlinOverridingCallableSearch.search(currentMethod, searchScope).forEach {
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
 * If processor returns `false`, the search is stopped and the result is `false`.
 */
@RequiresBackgroundThread(generateAssertion = false)
fun KtCallableDeclaration.processAllOverridings(
    processor: Processor<PsiElement>,
    searchScope: SearchScope = runReadActionBlocking { useScope },
): Boolean {
    return processAllOverridings(withFullHierarchy = false, processor, searchScope)
}

/**
 * Feed a set of PsiMethods/KtFunctions/KtProperties which belong to the hierarchy of a function and all its siblings to the processor.
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
fun KtCallableDeclaration.processHierarchyWithSiblings(
    processor: Processor<PsiElement>,
    searchScope: SearchScope = runReadActionBlocking { useScope }
): Boolean {
    return processAllOverridings(withFullHierarchy = true, processor, searchScope)
}


private fun KtCallableDeclaration.processAllOverridings(
    withFullHierarchy: Boolean,
    processor: Processor<PsiElement>,
    searchScope: SearchScope,
): Boolean {
    if (runReadActionBlocking { containingClassOrObject !is KtClass }) return true

    val queue = ArrayDeque<PsiElement>()
    val visited = HashSet<PsiElement>()

    queue.add(this)
    while (!queue.isEmpty()) {
        val currentMethod = queue.poll()
        when (currentMethod) {
            is KtCallableDeclaration -> {
                val isFinal = runReadActionBlocking {
                    analyze(currentMethod) {
                        val symbol = currentMethod.symbol
                        ((symbol as? KaValueParameterSymbol)?.generatedPrimaryConstructorProperty
                            ?: symbol).modality == KaSymbolModality.FINAL
                    }
                }
                if (isFinal) continue
                if (!DirectKotlinOverridingCallableSearch.search(currentMethod, searchScope).forEach(Processor<PsiElement> {
                        if (!visited.add(it)) return@Processor true
                        queue.offer(it)
                        processor.process(it)
                    })) return false
                if (withFullHierarchy) {
                    val overridden = analyze(currentMethod) {
                        val ktCallableSymbol = currentMethod.symbol as? KaCallableSymbol ?: return@analyze emptyList()
                        ktCallableSymbol.directlyOverriddenSymbols.mapNotNull { it.psi }.toList()
                    }
                    for (psi in overridden) {
                        if (!visited.add(psi)) continue
                        queue.offer(psi)
                        if (!processor.process(psi)) return false
                    }
                }
            }

            is PsiMethod -> {
                if (!OverridingMethodsSearch.search(currentMethod, searchScope, true)
                        .mappingNotNull { it.unwrapped }
                        .forEach(Processor<PsiElement> {
                            if (!visited.add(it)) return@Processor true
                            queue.offer(it)
                            processor.process(it)
                        })
                ) return false
                if (withFullHierarchy) {
                    for (superMethod in currentMethod.findSuperMethods(true)) {
                        val unwrapped = superMethod.unwrapped ?: continue
                        if (!visited.add(unwrapped)) continue
                        queue.offer(unwrapped)
                        if (!processor.process(unwrapped)) return false
                    }
                }
            }
        }

    }
    return true
}

/**
 * Finds all the classes inheriting from [this] element.
 * Note: The element must either be a [KtClass] or [PsiClass], otherwise an empty sequence is returned.
 * The resulting set does not include the class itself.
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
fun PsiElement.findAllInheritors(searchScope: SearchScope = useScope): Sequence<PsiElement> {
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
                    val isFinal = runReadActionBlocking {
                        !currentClass.isEnum() && analyze(currentClass) { // allow searching for enum constants in enum but don't bother about other final classes
                            currentClass.symbol.modality == KaSymbolModality.FINAL
                        }
                    }
                    if (isFinal) continue
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

/**
 * See [PsiElement.findAllInheritors]
 */
@RequiresBackgroundThread(generateAssertion = false)
fun KtClass.findAllInheritors(searchScope: SearchScope = useScope): Sequence<PsiElement> =
    (this as PsiElement).findAllInheritors(searchScope)

/**
 * If processor returns `false`, the search is stopped and the result is `false`.
 */
@RequiresBackgroundThread(generateAssertion = false)
fun PsiElement.processAllInheritors(processor: Processor<PsiElement>, searchScope: SearchScope = useScope): Boolean {
    val queue = ArrayDeque<PsiElement>()
    val visited = HashSet<PsiElement>()

    queue.add(this)

    while (!queue.isEmpty()) {
        val currentClass = queue.poll()

        when (currentClass) {
            is KtClass -> {
                val isFinal = runReadActionBlocking {
                    !currentClass.isEnum() && analyze(currentClass) { // allow searching for enum constants in enum but don't bother about other final classes
                        currentClass.symbol.modality == KaSymbolModality.FINAL
                    }
                }
                if (isFinal) continue
                if (!DirectKotlinClassInheritorsSearch.search(currentClass, searchScope).forEach(Processor<PsiElement> {
                        if (!visited.add(it)) return@Processor true
                        queue.offer(it)
                        processor.process(it)
                    })) {
                    return false
                }
            }

            is PsiClass -> {
                if (!ClassInheritorsSearch.search(currentClass, searchScope, /* checkDeep = */ false)
                        .mappingNotNull { it.unwrapped }
                        .forEach(Processor<PsiElement> {
                            if (!visited.add(it)) return@Processor true
                            queue.offer(it)
                            processor.process(it)
                        })
                ) {
                    return false
                }
            }
        }
    }
    return true
}

@RequiresBackgroundThread(generateAssertion = false)
fun KtClass.hasAnyInheritors(): Boolean {
    fun hasFound(): Boolean {
        val findProcessor = CommonProcessors.FindFirstProcessor<PsiElement>()
        processAllInheritors(findProcessor)
        val found = findProcessor.isFound
        return found
    }

    if (!isPhysical) return hasFound()
    return CachedValuesManager.getCachedValue(this) {
        CachedValueProvider.Result.create(
            hasFound(),
            KotlinUastOutOfCodeBlockModificationTracker.getInstance(project)
        )
    }
}

/**
 * Counts processed elements and stops once [maxCount] of them have been seen.
 * Only the count is retained; elements are not collected.
 */
class LimitedCountingProcessor(private val maxCount: Int) : Processor<PsiElement> {
    private val count = AtomicInteger(0)

    override fun process(t: PsiElement?): Boolean = count.incrementAndGet() < maxCount

    fun getCount(): Int = count.get()
}