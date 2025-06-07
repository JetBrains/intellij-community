// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.safeDelete.targetApiImpl

import com.intellij.find.usages.api.PsiUsage
import com.intellij.model.search.LeafOccurrence
import com.intellij.model.search.LeafOccurrenceMapper
import com.intellij.model.search.SearchContext
import com.intellij.model.search.SearchService
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.createSmartPointer
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.walkUp
import com.intellij.refactoring.safeDelete.api.PsiSafeDeleteUsage
import com.intellij.refactoring.safeDelete.api.SafeDeleteSearchParameters
import com.intellij.refactoring.safeDelete.api.SafeDeleteUsage
import com.intellij.refactoring.safeDelete.api.SafeDeleteUsageSearcher
import com.intellij.refactoring.safeDelete.impl.DefaultPsiSafeDeleteUsage
import com.intellij.util.AbstractQuery
import com.intellij.util.Processor
import com.intellij.util.Query
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolModality
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.parameterIndex

@Suppress("unused")
class KotlinSafeDeleteUsageSearcher : SafeDeleteUsageSearcher {
    override fun collectSearchRequests(parameters: SafeDeleteSearchParameters): Collection<Query<out SafeDeleteUsage>> {
        val target = parameters.target as? KotlinSafeDeleteTarget ?: return emptyList()

        val project = parameters.project
        val result = ArrayList<Query<out SafeDeleteUsage>>()
        val ktElement = target.ktElement
        if (ktElement is KtParameter) {
            val ktFunction = ktElement.ownerFunction as? KtFunction
            if (ktFunction != null) {
                val parameterIndex = ktElement.parameterIndex()
                result.add(SearchService.getInstance()
                               .searchWord(project, ktFunction.nameAsSafeName.asString())
                               .inScope(target.searchScope ?: GlobalSearchScope.projectScope(project))
                               .inContexts(SearchContext.inCode())
                               .inFilesWithLanguage(KotlinLanguage.INSTANCE)
                               .buildQuery(LeafOccurrenceMapper.withPointer(ktFunction.createSmartPointer()) { function, occurrence ->
                                   findArguments(function, occurrence, parameterIndex)
                               })
                )
            }
        }
        if (ktElement is KtNamedDeclaration) {
            result.add(
                SearchService.getInstance()
                    .searchWord(project, ktElement.nameAsSafeName.asString())
                    .inScope(target.searchScope ?: GlobalSearchScope.projectScope(project))
                    .inContexts(SearchContext.inCode())
                    .buildQuery(LeafOccurrenceMapper.withPointer(ktElement.createSmartPointer(), ::findReferences))
            )
        }
        
        if (ktElement is KtTypeParameter) {
            val owner = ktElement.getNonStrictParentOfType<KtTypeParameterListOwner>()
            if (owner != null) {
                val parameterList = owner.typeParameters
                val parameterIndex = parameterList.indexOf(ktElement)
                result.add(SearchService.getInstance()
                               .searchWord(project, owner.nameAsSafeName.asString())
                               .inScope(target.searchScope ?: GlobalSearchScope.projectScope(project))
                               .inContexts(SearchContext.inCode())
                               .inFilesWithLanguage(KotlinLanguage.INSTANCE)
                               .buildQuery(LeafOccurrenceMapper.withPointer(owner.createSmartPointer()) { parameterOwner, occurrence ->
                                   findTypeArguments(parameterOwner, occurrence, parameterIndex)
                               })
                )
            }
        }

        if (ktElement is KtCallableDeclaration) {
            result.add(object : AbstractQuery<SafeDeleteUsage>() {
                override fun processResults(consumer: Processor<in SafeDeleteUsage>): Boolean {
                    return runReadAction {
                        val classOrObject = ktElement.containingClassOrObject
                        analyze(ktElement) {
                            val elementClassSymbol = classOrObject?.symbol as? KaClassSymbol ?: return@analyze
                            val superMethods =
                                (ktElement.symbol as? KaCallableSymbol)?.directlyOverriddenSymbols ?: return@analyze
                            val abstractExternalSuper = superMethods.find {
                                val superClassSymbol = it.containingDeclaration as? KaClassSymbol ?: return@find false
                                if (it.modality != KaSymbolModality.ABSTRACT) return@find false
                                return@find !superClassSymbol.isSubClassOf(elementClassSymbol)
                            }

                            if (abstractExternalSuper != null) {
                                val psi = abstractExternalSuper.psi
                                if (psi != null && !consumer.process(
                                        DefaultPsiSafeDeleteUsage(
                                            PsiUsage.textUsage(
                                                psi.containingFile,
                                                psi.textRange
                                            ), false, "Overrides abstract $psi" //todo
                                        )
                                    )
                                ) {
                                    return@runReadAction false
                                }
                            }
                        }
                        return@runReadAction true
                    }
                }
            })
        }

        return result
    }

    private fun findTypeArguments(parameterOwner: KtTypeParameterListOwner, occurrence: LeafOccurrence, parameterIndex: Int): Collection<SafeDeleteUsage> {
        val (scope, start, offset) = occurrence
        val result = ArrayList<SafeDeleteUsage>()
        for ((element, _) in walkUp(start, offset, scope)) {
            for (reference in element.references) {
                if (reference.isReferenceTo(parameterOwner)) {
                    val referencedElement = reference.element
                    val argList = referencedElement.getNonStrictParentOfType<KtUserType>()?.typeArgumentList
                        ?: referencedElement.getNonStrictParentOfType<KtCallExpression>()?.typeArgumentList

                    if (argList != null) {
                        val projections = argList.arguments
                        if (parameterIndex < projections.size) {
                            result.add(SafeDeleteKotlinTypeArgumentUsage(projections[parameterIndex]))
                        }
                    }
                }
            }
        }
        return result
    }

    private fun findArguments(ktFunction: KtFunction, leafOccurrence: LeafOccurrence, parameterIndex: Int): Collection<SafeDeleteUsage> {
        val (scope, start, offset) = leafOccurrence
        val result = ArrayList<SafeDeleteUsage>()
        for ((element, _) in walkUp(start, offset, scope)) {
            for (reference in element.references) {
                if (reference.isReferenceTo(ktFunction)) {
                    val callExpression = reference.element.getNonStrictParentOfType<KtCallExpression>() ?: continue
                    val argument = callExpression.valueArguments[parameterIndex]
                    result.add(PsiSafeDeleteUsage.defaultPsiSafeDeleteUsage(argument, true))
                }
            }
        }
        return result
    }

    private fun findReferences(declaration: KtNamedDeclaration, leafOccurrence: LeafOccurrence): Collection<SafeDeleteUsage> {
        val (scope, start, offset) = leafOccurrence
        val result = ArrayList<SafeDeleteUsage>()
        for ((element, _) in walkUp(start, offset, scope)) {
            for (reference in element.references) {
                if (reference.isReferenceTo(declaration)) {
                    result.add(PsiSafeDeleteUsage.defaultPsiSafeDeleteUsage(PsiUsage.textUsage(reference.element, reference.rangeInElement), false))
                }
            }
        }
        return result
    }
}
