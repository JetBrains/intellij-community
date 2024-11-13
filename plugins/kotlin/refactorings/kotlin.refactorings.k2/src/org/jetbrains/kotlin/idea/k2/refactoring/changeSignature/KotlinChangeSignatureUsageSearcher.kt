// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.changeSignature

import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.util.RefactoringUIUtil
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.*
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaReceiverParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.useScope
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.usages.*
import org.jetbrains.kotlin.idea.k2.refactoring.getThisQualifier
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.kdoc.psi.impl.KDocName
import org.jetbrains.kotlin.psi.*
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
            val oldIndex = parameterInfo.oldIndex - receiverOffset
            if (oldIndex >= 0 && oldIndex < oldSignatureParameters.size) {
                val oldParam = oldSignatureParameters[oldIndex]
                if (parameterInfo == changeInfo.receiverParameterInfo ||
                    parameterInfo.oldName != parameterInfo.name ||
                    isDataClass && i != parameterInfo.oldIndex
                ) {
                    for (reference in ReferencesSearch.search(oldParam, oldParam.useScope())) {
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
        }
        if (isDataClass && changeInfo is KotlinChangeInfo && !changeInfo.hasAppendedParametersOnly()) {
            ktCallableDeclaration.valueParameters.firstOrNull()?.let {
                ReferencesSearch.search(it).mapNotNullTo(result) { reference ->
                    val destructuringEntry = reference.element as? KtDestructuringDeclarationEntry ?: return@mapNotNullTo null
                    KotlinComponentUsageInDestructuring(destructuringEntry)
                }
            }
        }
        if (ktCallableDeclaration is KtFunction &&
            changeInfo is KotlinChangeInfo &&
            (changeInfo.oldReceiverInfo == null || changeInfo.newParameters.contains(changeInfo.oldReceiverInfo)) &&
            changeInfo.receiverParameterInfo?.oldIndex != changeInfo.oldReceiverInfo?.oldIndex
        ) {
            findReceiverReferences(ktCallableDeclaration, result, changeInfo)
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
                                    else if (receiverValue is KaImplicitReceiverValue) {
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
