// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.searching.inheritors

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.LambdaUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.DefinitionsScopedSearch
import com.intellij.psi.search.searches.FunctionalExpressionSearch
import com.intellij.util.Processor
import com.intellij.util.QueryExecutor
import org.jetbrains.kotlin.asJava.elements.KtLightMethod
import org.jetbrains.kotlin.asJava.toFakeLightClass
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.idea.findUsages.KotlinFindUsagesSupport
import org.jetbrains.kotlin.idea.search.ExpectActualUtils.actualsForExpect
import org.jetbrains.kotlin.idea.search.ExpectActualUtils.expectDeclarationIfAny
import org.jetbrains.kotlin.idea.search.declarationsSearch.toPossiblyFakeLightMethods
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtSecondaryConstructor
import org.jetbrains.kotlin.psi.psiUtil.contains
import org.jetbrains.kotlin.psi.psiUtil.isExpectDeclaration

class KotlinFirDefinitionsSearcher : QueryExecutor<PsiElement, DefinitionsScopedSearch.SearchParameters> {
    override fun execute(queryParameters: DefinitionsScopedSearch.SearchParameters, consumer: Processor<in PsiElement>): Boolean {
        val processor = skipDelegatedMethodsConsumer(consumer)
        val element = runReadAction { queryParameters.element.originalElement }
        val scope = queryParameters.scope

        return when (element) {
            is KtClassOrObject -> {
                val isExpectEnum = runReadAction { element is KtClass && element.isEnum() && element.isExpectDeclaration() }
                if (isExpectEnum) {
                    processActualDeclarations(element, processor)
                } else {
                    val expectedClassOrObject = runReadAction { element.expectDeclarationIfAny() as? KtClassOrObject ?: element }
                    val searchScope = includeCommonPlatformIfNeeded(scope, element, expectedClassOrObject)
                    processClassImplementations(expectedClassOrObject, searchScope, processor) &&
                            processActualDeclarations(expectedClassOrObject) { actual ->
                                if (element == expectedClassOrObject && !processor.process(actual)) return@processActualDeclarations false
                                actual !is KtClass || processClassImplementations(actual, searchScope, processor)
                            }
                }
            }

            is KtNamedFunction, is KtSecondaryConstructor -> {
                val expectedFunction = runReadAction { element.expectDeclarationIfAny() as? KtFunction ?: element }
                val searchScope = includeCommonPlatformIfNeeded(scope, element, expectedFunction)
                processFunctionImplementations(expectedFunction, searchScope, processor) &&
                    processActualDeclarations(expectedFunction) { actual ->
                        if (element == expectedFunction && !processor.process(actual)) return@processActualDeclarations false
                        actual !is KtFunction || processFunctionImplementations(actual, searchScope, processor)
                    }
            }

            is KtProperty -> {
                processProperty(element, scope, processor)
            }

            is KtParameter -> {
                if (isFieldParameter(element)) processProperty(element, scope, processor) else true
            }

            else -> true
        }
    }

    private fun processProperty(
        element: KtCallableDeclaration,
        scope: SearchScope,
        processor: Processor<PsiElement>
    ): Boolean {
        val expectedCallable = runReadAction { element.expectDeclarationIfAny() as? KtCallableDeclaration ?: element }
        val searchScope = includeCommonPlatformIfNeeded(scope, element, expectedCallable)
        return processPropertyImplementations(expectedCallable, searchScope, processor) &&
                processActualDeclarations(expectedCallable) { actual ->
                    if (element == expectedCallable && !processor.process(actual)) return@processActualDeclarations false
                    actual !is KtProperty || processPropertyImplementations(actual, searchScope, processor)
                }
    }

    private fun includeCommonPlatformIfNeeded(
        scope: SearchScope,
        element: KtDeclaration,
        expectedDeclaration: KtDeclaration
    ): SearchScope {
        return runReadAction {
            if (element != expectedDeclaration) {
                scope.union(expectedDeclaration.useScope.intersectWith(element.resolveScope))
            } else {
                scope
            }
        }
    }
}

private fun skipDelegatedMethodsConsumer(baseConsumer: Processor<in PsiElement>): Processor<PsiElement> = Processor { element ->
    if (isDelegated(element)) {
        return@Processor true
    }

    baseConsumer.process(element)
}

private fun isDelegated(element: PsiElement): Boolean = element is KtLightMethod && element.isDelegated

private fun isFieldParameter(parameter: KtParameter): Boolean = runReadAction {
    KtPsiUtil.getClassIfParameterIsProperty(parameter) != null
}

private fun processClassImplementations(klass: KtClassOrObject, searchScope: SearchScope, consumer: Processor<PsiElement>): Boolean {
    if (klass !is KtClass) return true

    if (searchScope is LocalSearchScope) {
        return processLightClassLocalImplementations(klass, searchScope, consumer)
    }

    if (!KotlinFindUsagesSupport.searchInheritors(klass, searchScope).all { consumer.process(it) }) {
        return false
    }

    val lightClass = runReadAction { (klass.toLightClass() ?: klass.toFakeLightClass()).takeIf { LambdaUtil.isFunctionalClass(it) }}
    if (lightClass != null) {
        return FunctionalExpressionSearch.search(lightClass).asIterable().all { consumer.process(it) }
    }
    return true
}

private fun processLightClassLocalImplementations(
    ktClass: KtClass,
    searchScope: LocalSearchScope,
    consumer: Processor<PsiElement>
): Boolean {
    // workaround for IDEA optimization that uses Java PSI traversal to locate inheritors in local search scope
    val globalScope = runReadAction {
        val virtualFiles = searchScope.scope.mapTo(HashSet()) { it.containingFile.virtualFile }
        GlobalSearchScope.filesScope(ktClass.project, virtualFiles)
    }
    return ktClass.findAllInheritors(globalScope).all { candidate ->
        val candidateOrigin = candidate.unwrapped ?: candidate
        val inScope = runReadAction { candidateOrigin in searchScope }
        if (inScope) {
            consumer.process(candidate)
        } else {
            true
        }
    }
}

private fun processFunctionImplementations(
    function: KtFunction,
    scope: SearchScope,
    consumer: Processor<PsiElement>,
): Boolean {
    if (!function.findAllOverridings(scope).all { consumer.process(it) }) return false

    val method = runReadAction { function.toPossiblyFakeLightMethods().firstOrNull() } ?: return true
    return FunctionalExpressionSearch.search(method, scope).forEach(consumer)
}

private fun processPropertyImplementations(
    declaration: KtCallableDeclaration,
    scope: SearchScope,
    consumer: Processor<PsiElement>
): Boolean = declaration.findAllOverridings(scope).all { implementation ->
    if (isDelegated(implementation)) return@all true

    val elementToProcess = runReadAction {
        when (val mirrorElement = (implementation as? KtLightMethod)?.kotlinOrigin) {
            is KtProperty, is KtParameter -> mirrorElement
            is KtPropertyAccessor -> if (mirrorElement.parent is KtProperty) mirrorElement.parent else implementation
            else -> implementation
        }
    }

    consumer.process(elementToProcess)
}

private fun processActualDeclarations(declaration: KtDeclaration, consumer: Processor<PsiElement>): Boolean = runReadAction {
    if (!declaration.isExpectDeclaration()) true
    else declaration.actualsForExpect().all(consumer::process)
}

