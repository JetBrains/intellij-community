// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.changeSignature

import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.calls.*
import org.jetbrains.kotlin.analysis.api.symbols.KtClassOrObjectSymbol
import org.jetbrains.kotlin.idea.base.util.useScope
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.usages.*
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
                            result.add(KotlinParameterUsage(element as KtElement, parameterInfo))
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
            changeInfo.receiverParameterInfo?.oldIndex != changeInfo.oldReceiverInfo?.oldIndex
        ) {
            findReceiverReferences(ktCallableDeclaration, result, changeInfo)
        }
    }

    internal fun findReceiverReferences(ktCallableDeclaration: KtCallableDeclaration, result: MutableList<in UsageInfo>, changeInfo: KotlinChangeInfo) {
        analyze(ktCallableDeclaration) {
            val originalReceiverInfo = changeInfo.oldReceiverInfo
            val originalReceiverType = ktCallableDeclaration.receiverTypeReference?.getKtType()
            ktCallableDeclaration.accept(object : KtTreeVisitorVoid() {

                override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
                    super.visitSimpleNameExpression(expression)

                    val call = expression.resolveCall()

                    if (call == null && originalReceiverType != null) {
                        //deleted or changed receiver, must be preserved as simple parameter
                        val parentExpression = expression.parent
                        if (parentExpression is KtThisExpression && parentExpression.parent !is KtDotQualifiedExpression &&
                            parentExpression.getKtType()?.let { originalReceiverType.isEqualTo(it) } == true) {
                            result.add(
                                KotlinParameterUsage(parentExpression, originalReceiverInfo!!)
                            )
                            return
                        }
                    }

                    val partiallyAppliedSymbol = (call?.singleVariableAccessCall() ?: call?.singleFunctionCallOrNull())?.partiallyAppliedSymbol

                    if (partiallyAppliedSymbol != null) {
                        val receiverValue = partiallyAppliedSymbol.extensionReceiver ?: partiallyAppliedSymbol.dispatchReceiver
                        val containingSymbol = partiallyAppliedSymbol.symbol.getContainingSymbol()
                        if (receiverValue != null) {
                            val receiverExpression = (receiverValue as? KtExplicitReceiverValue)?.expression
                                ?: ((receiverValue as? KtSmartCastedReceiverValue)?.original as? KtExplicitReceiverValue)?.expression
                                ?: expression
                            if (originalReceiverType != null) {
                                if (receiverValue.type.isSubTypeOf(originalReceiverType)) {
                                    if (receiverExpression is KtThisExpression) {
                                        result.add(KotlinParameterUsage(receiverExpression, originalReceiverInfo!!))
                                    }
                                    else {
                                        result.add(KotlinImplicitThisToParameterUsage(receiverExpression, originalReceiverInfo!!))
                                    }
                                }
                            }
                            else  {
                                val name = (containingSymbol as? KtClassOrObjectSymbol)?.name
                                if (name != null) {
                                    if (receiverExpression is KtThisExpression) {
                                        result.add(KotlinNonQualifiedOuterThisUsage(receiverExpression, name))
                                    }
                                    else if (receiverValue is KtImplicitReceiverValue && partiallyAppliedSymbol.extensionReceiver == null) {
                                        result.add(KotlinImplicitThisUsage(receiverExpression, name))
                                    }
                                }
                            }
                        }
                    }
                }
            })
        }
    }

}
