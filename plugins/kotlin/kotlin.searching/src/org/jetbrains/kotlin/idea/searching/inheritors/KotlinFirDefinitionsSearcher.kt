// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.searching.inheritors

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.DefinitionsScopedSearch
import com.intellij.psi.search.searches.FunctionalExpressionSearch
import com.intellij.util.Processor
import com.intellij.util.QueryExecutor
import org.jetbrains.kotlin.asJava.elements.KtLightMethod
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.idea.base.psi.isExpectDeclaration
import org.jetbrains.kotlin.idea.search.ExpectActualUtils.actualsForExpected
import org.jetbrains.kotlin.idea.search.declarationsSearch.toPossiblyFakeLightMethods
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.contains
import java.util.concurrent.Callable

class KotlinFirDefinitionsSearcher : QueryExecutor<PsiElement, DefinitionsScopedSearch.SearchParameters> {
    override fun execute(queryParameters: DefinitionsScopedSearch.SearchParameters, consumer: Processor<in PsiElement>): Boolean {
        val processor = skipDelegatedMethodsConsumer(consumer)
        val element = runReadAction { queryParameters.element.originalElement }
        val scope = queryParameters.scope

        return when (element) {
            is KtClass -> {
                val isExpectEnum = runReadAction { element.isEnum() && element.isExpectDeclaration() }
                if (isExpectEnum) {
                    processActualDeclarations(element, processor)
                } else {
                    processClassImplementations(element, processor) && processActualDeclarations(element, processor)
                }
            }

            is KtObjectDeclaration -> {
                processActualDeclarations(element, processor)
            }

            is KtNamedFunction, is KtSecondaryConstructor -> {
                processFunctionImplementations(element as KtFunction, scope, processor) && processActualDeclarations(element, processor)
            }

            is KtProperty -> {
                processPropertyImplementations(element, scope, processor) && processActualDeclarations(element, processor)
            }

            is KtParameter -> {
                if (isFieldParameter(element)) {
                    processPropertyImplementations(element, scope, processor) && processActualDeclarations(element, processor)
                } else {
                    true
                }
            }

            else -> true
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

private fun processClassImplementations(klass: KtClass, consumer: Processor<PsiElement>): Boolean {

    val searchScope = runReadAction { klass.useScope }
    if (searchScope is LocalSearchScope) {
        return processLightClassLocalImplementations(klass, searchScope, consumer)
    }

    return klass.findAllInheritors(searchScope).all { runReadAction { consumer.process(it)} }
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
): Boolean =
    ReadAction.nonBlocking(Callable {
        if (!function.findAllOverridings(scope).all { consumer.process(it) }) return@Callable false

        val method = function.toPossiblyFakeLightMethods().firstOrNull() ?: return@Callable true
        FunctionalExpressionSearch.search(method, scope).forEach(consumer)
    }).executeSynchronously()

private fun processPropertyImplementations(
    declaration: KtCallableDeclaration,
    scope: SearchScope,
    consumer: Processor<PsiElement>
): Boolean = ReadAction.nonBlocking(Callable {
    processPropertyImplementationsMethods(declaration, scope, consumer)
}).executeSynchronously()

private fun processActualDeclarations(declaration: KtDeclaration, consumer: Processor<PsiElement>): Boolean = runReadAction {
    if (!declaration.isExpectDeclaration()) true
    else declaration.actualsForExpected().all(consumer::process)
}

private fun processPropertyImplementationsMethods(
    callableDeclaration: KtCallableDeclaration,
    scope: SearchScope,
    consumer: Processor<PsiElement>
): Boolean =
    callableDeclaration.findAllOverridings(scope).all { implementation ->
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
