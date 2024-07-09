// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.k2.refactoring.inline.codeInliner

import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.resolution.KaCallableMemberCall
import org.jetbrains.kotlin.analysis.api.resolution.singleCallOrNull
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.UsageReplacementStrategy
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.types.Variance

class TypeAliasUsageReplacementStrategy(val typeAlias: KtTypeAlias) : UsageReplacementStrategy {
    @OptIn(KaAllowAnalysisFromWriteAction::class, KaAllowAnalysisOnEdt::class) //under potemkin progress
    override fun createReplacer(usage: KtReferenceExpression): (() -> KtElement?)? {
        val refElement = usage.getParentOfTypeAndBranch<KtUserType> { referenceExpression }
            ?: usage.getNonStrictParentOfType<KtSimpleNameExpression>() ?: return null

        return when (refElement) {
            is KtUserType -> {
                {
                    allowAnalysisOnEdt {
                        allowAnalysisFromWriteAction {
                            inlineIntoType(refElement)
                        }
                    }
                }
            }

            else -> {
                {
                    allowAnalysisOnEdt {
                        allowAnalysisFromWriteAction {
                            inlineIntoCall(refElement as KtReferenceExpression)
                        }
                    }
                }
            }
        }
    }

    @OptIn(KaExperimentalApi::class)
    fun inlineIntoCall(usage: KtReferenceExpression): KtElement? {
        analyze(usage) {
            val importDirective = usage.getStrictParentOfType<KtImportDirective>()
            if (importDirective != null) {
                val reference = usage.getQualifiedElementSelector()?.mainReference
                if (reference != null && reference.multiResolve(false).size <= 1) {
                    importDirective.delete()
                }

                return null
            }

            val typeAliasSymbol = typeAlias.symbol
            val typeToInline = typeAliasSymbol.expandedType

            val callElement = usage.parent as? KtCallElement
            val expandedTypeFqName = if (callElement != null) {
                val callableCall = usage.resolveToCall()?.singleCallOrNull<KaCallableMemberCall<*, *>>() ?: return null
                val substitution = callableCall.typeArgumentsMapping
                if (substitution.size != typeAliasSymbol.typeParameters.size) return null
                val substitutor = createSubstitutor(substitution)

                val expandedType = substitutor.substitute(typeToInline)

                expandedType.expandedSymbol?.importableFqName?.also {
                    val typeArguments = (expandedType as? KaClassType)?.typeArguments
                    if (typeArguments != null && typeArguments.isNotEmpty()) {
                        val expandedTypeArgumentList = KtPsiFactory(typeAlias.project).createTypeArguments(typeArguments.joinToString(
                            prefix = "<", postfix = ">"
                        ) { it.type?.render(position = Variance.INVARIANT) ?: "*" })

                        val originalTypeArgumentList = callElement.typeArgumentList
                        if (originalTypeArgumentList != null) {
                            shortenReferences(originalTypeArgumentList.replaced(expandedTypeArgumentList))
                        } else {
                            shortenReferences(callElement.addAfter(expandedTypeArgumentList, callElement.calleeExpression) as KtElement)
                        }
                    }
                }
            } else {
                typeToInline.expandedSymbol?.importableFqName
            } ?: return null


            val newCallElement = ((usage.mainReference as KtSimpleNameReference).bindToFqName(
                expandedTypeFqName
            ) as KtExpression).getNonStrictParentOfType<KtCallElement>()
            return newCallElement?.getQualifiedExpressionForSelector() ?: newCallElement
        }
    }


    @OptIn(KaExperimentalApi::class)
    private fun inlineIntoType(usage: KtUserType): KtElement? {
        analyze(usage) {
            val typeAliasSymbol = typeAlias.symbol
            val typeToInline = typeAliasSymbol.expandedType
            val typeParameters = typeAliasSymbol.typeParameters

            val psiFactory = KtPsiFactory(typeAlias.project)

            val argumentTypes = usage
                .typeArguments
                .asSequence()
                .filterNotNull()
                .mapNotNull {
                    it.typeReference?.type
                }
                .toList()

            if (argumentTypes.size != typeParameters.size) return null
            val substitution = (typeParameters zip argumentTypes).toMap()

            val substitutor = createSubstitutor(substitution)
            val expandedType = substitutor.substitute(typeToInline)
            val expandedTypeText = expandedType.render(position = Variance.INVARIANT)
            val needParentheses =
                (expandedType.isFunctionType && usage.parent is KtNullableType) || (expandedType.isExtensionFunctionType() && usage.getParentOfTypeAndBranch<KtFunctionType> { receiverTypeReference } != null)
            val expandedTypeReference = psiFactory.createType(expandedTypeText)
            return usage.replaced(expandedTypeReference.typeElement!!).apply {
                if (needParentheses) {
                    val sample = psiFactory.createParameterList("()")
                    parent.addBefore(sample.firstChild, this)
                    parent.addAfter(sample.lastChild, this)
                }
                shortenReferences(this)
            }
        }
    }


}

private fun KaType.isExtensionFunctionType(): Boolean {
    val functionalType = this as? KaFunctionType ?: return false
    return functionalType.hasReceiver
}