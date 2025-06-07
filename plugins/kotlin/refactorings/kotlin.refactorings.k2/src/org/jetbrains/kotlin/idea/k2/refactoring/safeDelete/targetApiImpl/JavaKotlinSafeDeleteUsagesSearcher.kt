// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.safeDelete.targetApiImpl

import com.intellij.lang.java.JavaLanguage
import com.intellij.model.search.LeafOccurrence
import com.intellij.model.search.LeafOccurrenceMapper
import com.intellij.model.search.SearchContext
import com.intellij.model.search.SearchService
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.walkUp
import com.intellij.refactoring.safeDelete.api.PsiSafeDeleteUsage
import com.intellij.refactoring.safeDelete.api.SafeDeleteSearchParameters
import com.intellij.refactoring.safeDelete.api.SafeDeleteUsage
import com.intellij.refactoring.safeDelete.api.SafeDeleteUsageSearcher
import com.intellij.util.Query
import org.jetbrains.kotlin.asJava.toPsiParameters
import org.jetbrains.kotlin.asJava.toPsiTypeParameters
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtTypeParameter
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.parameterIndex

@Suppress("unused")
class JavaKotlinSafeDeleteUsagesSearcher : SafeDeleteUsageSearcher {
    override fun collectSearchRequests(parameters: SafeDeleteSearchParameters): Collection<Query<out SafeDeleteUsage>> {
        val target = parameters.target as? KotlinSafeDeleteTarget ?: return emptyList()

        val ktElement = target.ktElement
        val result = ArrayList<Query<out SafeDeleteUsage>>()

        val project = parameters.project
        if (ktElement is KtParameter) {

            for (psiParameter in ktElement.toPsiParameters()) {
                val psiMethod = psiParameter.declarationScope as? PsiMethod ?: continue
                val parameterIndex = psiParameter.parameterIndex()
                result.add(
                    SearchService.getInstance()
                               .searchWord(project, psiMethod.name)
                               .inScope(target.searchScope ?: GlobalSearchScope.projectScope(project))
                               .inContexts(SearchContext.inCode())
                               .inFilesWithLanguageOfKind(JavaLanguage.INSTANCE)
                               .buildQuery(LeafOccurrenceMapper.withPointer(psiMethod.createSmartPointer()) { overriding, occurrence ->
                                   findArguments(overriding, occurrence, parameterIndex)
                               })
                )
            }
        }
        else if (ktElement is KtTypeParameter) {
            for (typeParameter in ktElement.toPsiTypeParameters()) {
                val parameterIndex = typeParameter.index
                val owner = typeParameter.owner ?: continue
                val ownerName = owner.name ?: continue
                result.add(
                    SearchService.getInstance()
                               .searchWord(project, ownerName)
                               .inScope(target.searchScope ?: GlobalSearchScope.projectScope(project))
                               .inFilesWithLanguageOfKind(JavaLanguage.INSTANCE)
                               .inContexts(SearchContext.inCode())
                               .buildQuery(LeafOccurrenceMapper.withPointer(owner.createSmartPointer()) { dereferencedOwner, occurrence ->
                                   findTypeArguments(dereferencedOwner, occurrence, parameterIndex)
                               })
                )
            }
        }

        return result
    }

    private fun findTypeArguments(dereferencedOwner: PsiTypeParameterListOwner, occurrence: LeafOccurrence, parameterIndex: Int): Collection<SafeDeleteUsage> {
        val (scope, start, offset) = occurrence
        val result = ArrayList<SafeDeleteUsage>()
        for ((element, _) in walkUp(start, offset, scope)) {
            for (reference in element.references) {
                if (reference.isReferenceTo(dereferencedOwner) && reference is PsiJavaCodeReferenceElement) {
                    val parameterList = reference.parameterList ?: continue
                    val typeArgs = parameterList.typeParameterElements
                    if (typeArgs.size > parameterIndex) {
                        if (typeArgs.size == 1 && dereferencedOwner.typeParameters.size > 1 && typeArgs[0].type is PsiDiamondType) {
                            continue
                        }
                        result.add(PsiSafeDeleteUsage.defaultPsiSafeDeleteUsage(typeArgs[parameterIndex], true))
                    }
                }
            }
        }
        return result
    }

    private fun findArguments(overriding: PsiMethod, occurrence: LeafOccurrence, parameterIndex: Int) : Collection<SafeDeleteUsage> {
        val (scope, start, offset) = occurrence
        val result = ArrayList<SafeDeleteUsage>()
        for ((element, _) in walkUp(start, offset, scope)) {
            for (reference in element.references) {
                if (reference.isReferenceTo(overriding)) {
                    val callExpression = reference.element.getNonStrictParentOfType<PsiMethodCallExpression>() ?: continue
                    val argument = callExpression.argumentList.expressions[parameterIndex]
                    result.add(PsiSafeDeleteUsage.defaultPsiSafeDeleteUsage(argument, true))
                }
            }
        }
        return result
    }
}