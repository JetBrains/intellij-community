// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.changeSignature

import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.util.RefactoringUIUtil
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.*
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaContextParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.idea.base.analysis.api.utils.unwrapSmartCasts
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.useScope
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.usages.*
import org.jetbrains.kotlin.idea.k2.refactoring.getThisQualifier
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.kdoc.psi.impl.KDocName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.parameterIndex
import org.jetbrains.kotlin.util.OperatorNameConventions

internal object KotlinChangeSignatureUsageSearcher {
    fun findInternalUsages(
        ktCallableDeclaration: KtCallableDeclaration,
        changeInfo: KotlinChangeInfoBase,
        result: MutableList<in UsageInfo>
    ) {
        val isDataClass =
            ktCallableDeclaration is KtPrimaryConstructor && (ktCallableDeclaration.getContainingClassOrObject() as? KtClass)?.isData() == true
        val oldSignatureParameters = ktCallableDeclaration.valueParameters
        val receiverOffset = if (ktCallableDeclaration.receiverTypeReference != null) 1 else 0
        for ((i, parameterInfo) in changeInfo.newParameters.withIndex()) {
            val oldParam = if (parameterInfo.wasContextParameter) {
                ktCallableDeclaration.modifierList?.contextReceiverList?.contextParameters()?.getOrNull(parameterInfo.oldIndex)
            } else {
                val oldIndex = parameterInfo.oldIndex - receiverOffset
                if (oldIndex >= 0 && oldIndex < oldSignatureParameters.size) {
                    oldSignatureParameters[oldIndex]
                } else null
            } ?: continue
            if (parameterInfo == changeInfo.receiverParameterInfo ||
                parameterInfo.oldName != parameterInfo.name ||
                isDataClass && i != parameterInfo.oldIndex
            ) {
                for (reference in ReferencesSearch.search(oldParam, oldParam.useScope()).asIterable()) {
                    val element = reference.element
                    if (isDataClass &&
                        element is KtSimpleNameExpression &&
                        (element.parent as? KtCallExpression)?.calleeExpression == element &&
                        element.getReferencedName() != parameterInfo.name &&
                        OperatorNameConventions.COMPONENT_REGEX.matches(element.getReferencedName())
                    ) {
                        result.add(KotlinDataClassComponentUsage(element, "component${i + 1}"))
                    } else if ((element is KtSimpleNameExpression || element is KDocName) && element.parent !is KtValueArgumentName) {
                        result.add(KotlinParameterUsage(element, parameterInfo))
                    }
                }
            }
        }
        if (isDataClass && changeInfo is KotlinChangeInfo && !changeInfo.hasAppendedParametersOnly()) {
            ktCallableDeclaration.valueParameters.firstOrNull()?.let {
                ReferencesSearch.search(it).asIterable().mapNotNullTo(result) { reference ->
                    val destructuringEntry = reference.element as? KtDestructuringDeclarationEntry ?: return@mapNotNullTo null
                    KotlinComponentUsageInDestructuring(destructuringEntry)
                }
            }
        }
        if (ktCallableDeclaration is KtFunction &&
            changeInfo is KotlinChangeInfo &&
            (changeInfo.oldReceiverInfo == null || changeInfo.newParameters.any { it.oldIndex == changeInfo.oldReceiverInfo!!.oldIndex }) &&
            changeInfo.receiverParameterInfo?.oldIndex != changeInfo.oldReceiverInfo?.oldIndex
        ) {
            findReceiverReferences(ktCallableDeclaration, result, changeInfo)
        }

        if (changeInfo is KotlinChangeInfo) {
            val contextParameters = changeInfo.method.modifierList?.contextReceiverList?.contextParameters()
            val parameterInfos = changeInfo.newParameters.filter { it.isContextParameter }
            if ((contextParameters?.size ?: 0) != parameterInfos.size) {
                findContextParameterReferences(ktCallableDeclaration, result, changeInfo)
            }
        }
    }

    @OptIn(KaExperimentalApi::class)
    private fun findContextParameterReferences(
        ktCallableDeclaration: KtCallableDeclaration,
        result: MutableList<in UsageInfo>,
        changeInfo: KotlinChangeInfo
    ) {
        val oldContextParameters = changeInfo.methodDescriptor.parameters.filter { it.wasContextParameter }
        if (oldContextParameters.isEmpty()) return
        val preservedContextParameters = changeInfo.newParameters.filter { it.isContextParameter }
        analyze(ktCallableDeclaration) {
            ktCallableDeclaration.accept(object : KtTreeVisitorVoid() {
                override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
                    val memberCall = expression.resolveToCall()?.successfulCallOrNull<KaCallableMemberCall<*, *>>()
                    val partiallyAppliedSymbol = memberCall?.partiallyAppliedSymbol ?: return

                    if (partiallyAppliedSymbol.symbol is KaContextParameterSymbol) {
                        val contextParameter = partiallyAppliedSymbol.symbol.psi as? KtParameter ?: return
                        val parameterInfo =
                            oldContextParameters.find { it.oldIndex == contextParameter.parameterIndex() } ?: return
                        if (parameterInfo == changeInfo.receiverParameterInfo) {
                            result.add(KotlinParameterUsage(expression, parameterInfo))
                        }
                        return
                    }

                    val usedContextParameters =
                        partiallyAppliedSymbol
                            .contextArguments
                            .mapNotNull { receiverValue ->
                                val contextParameterSymbol =
                                    (receiverValue.unwrapSmartCasts() as? KaImplicitReceiverValue)?.symbol as? KaContextParameterSymbol
                                (contextParameterSymbol?.psi as? KtParameter)?.takeIf { it.isContextParameter }
                            }
                            .takeIf { it.isNotEmpty() } ?: return

                    val preservedIndexes = preservedContextParameters.map { it.oldIndex }
                    for (contextParameter in usedContextParameters) {
                        val index = contextParameter.parameterIndex()
                        if (index in preservedIndexes) continue
                        val parameterInfo = changeInfo.getNonReceiverParameters().find { it.wasContextParameter && it.oldIndex == index } ?: continue
                        result.add(KotlinContextParameterUsage(expression, parameterInfo))
                    }
                }
            })
        }
    }

    internal fun findReceiverReferences(ktCallableDeclaration: KtCallableDeclaration, result: MutableList<in UsageInfo>, changeInfo: KotlinChangeInfo) {
        analyze(ktCallableDeclaration) {
            val originalReceiverInfo = changeInfo.oldReceiverInfo
            val originalReceiverType = ktCallableDeclaration.receiverTypeReference?.type
            ktCallableDeclaration.accept(object : KtTreeVisitorVoid() {

                override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
                    super.visitSimpleNameExpression(expression)

                    val call = expression.resolveToCall()

                    if (call == null && originalReceiverType != null) {
                        //deleted or changed receiver, must be preserved as simple parameter
                        val parentExpression = expression.parent
                        if (parentExpression is KtThisExpression && parentExpression.parent !is KtDotQualifiedExpression &&
                            parentExpression.expressionType?.isSubtypeOf(originalReceiverType) == true) {
                            result.add(
                                KotlinParameterUsage(parentExpression, originalReceiverInfo!!)
                            )
                            return
                        }
                    }

                    val partiallyAppliedSymbol = (call?.singleVariableAccessCall() ?: call?.singleFunctionCallOrNull())?.partiallyAppliedSymbol

                    if (partiallyAppliedSymbol != null) {
                        val receiverValue = partiallyAppliedSymbol.extensionReceiver ?: partiallyAppliedSymbol.dispatchReceiver
                        val symbol = partiallyAppliedSymbol.symbol
                        val containingSymbol = symbol.containingDeclaration
                        if (receiverValue != null) {
                            val receiverExpression = (receiverValue as? KaExplicitReceiverValue)?.expression
                                ?: ((receiverValue as? KaSmartCastedReceiverValue)?.original as? KaExplicitReceiverValue)?.expression
                                ?: expression
                            if (originalReceiverType != null) {
                                if (receiverValue.type.isSubtypeOf(originalReceiverType)) {
                                    if (receiverExpression is KtThisExpression) {
                                        val targetLabel = receiverExpression.getTargetLabel()
                                        if (targetLabel == null || targetLabel.expressionType?.let { originalReceiverType.semanticallyEquals(it) } == true) {
                                            result.add(KotlinParameterUsage(receiverExpression, originalReceiverInfo!!))
                                        }
                                    }
                                    else if (receiverValue.unwrapSmartCasts() is KaImplicitReceiverValue) {
                                        result.add(KotlinImplicitThisToParameterUsage(receiverExpression, originalReceiverInfo!!))
                                    }
                                }
                            } else {
                                val name = (containingSymbol as? KaClassSymbol)?.name
                                if (name != null) {
                                    if (receiverExpression is KtThisExpression) {
                                        result.add(KotlinNonQualifiedOuterThisUsage(receiverExpression, name))
                                    } else if (receiverValue is KaImplicitReceiverValue && partiallyAppliedSymbol.extensionReceiver == null && receiverExpression is KtNameReferenceExpression) {
                                        result.add(KotlinImplicitThisUsage(receiverExpression, getThisQualifier(receiverValue)))
                                    }
                                }
                            }
                        } else if (symbol !is KaValueParameterSymbol && originalReceiverType == null) {
                            val declaration = symbol.psi
                            val receiverParameterInfo = changeInfo.receiverParameterInfo
                            require(receiverParameterInfo != null)
                            if (declaration != null) {
                                val currentType = receiverParameterInfo.currentType
                                val referenceThroughNewReceiver = "(___p___ as ${currentType.text}).${expression.getReferencedName()}"
                                val fragment = KtPsiFactory(declaration.project).createExpressionCodeFragment(
                                    referenceThroughNewReceiver, currentType.context
                                )
                                val dotQualifiedExpression = fragment.getContentElement() as? KtDotQualifiedExpression
                                if (dotQualifiedExpression?.selectorExpression?.mainReference?.resolve() != null) {
                                    val prefix = RefactoringUIUtil.getDescription(declaration, true)
                                    result.add(
                                        KotlinChangeSignatureConflictingUsageInfo(
                                            expression, KotlinBundle.message(
                                                "text.0.will.no.longer.be.accessible.after.signature.change", prefix.capitalize()
                                            )
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            })
        }
    }

}
