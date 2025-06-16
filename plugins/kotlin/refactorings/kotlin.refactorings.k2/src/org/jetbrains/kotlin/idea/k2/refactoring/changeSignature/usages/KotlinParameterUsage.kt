// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.usages

import com.intellij.psi.PsiElement
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.idea.base.analysis.api.utils.allowAnalysisFromWriteActionInEdt
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinChangeInfoBase
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinParameterInfo
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.forEachDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.isIdentifier

internal class KotlinParameterUsage(
    element: KtElement,
    private val parameterInfo: KotlinParameterInfo
) : UsageInfo(element), KotlinBaseChangeSignatureUsage {
    override fun processUsage(
        changeInfo: KotlinChangeInfoBase,
        element: KtElement,
        allUsages: Array<out UsageInfo>
    ): KtElement {
        val newElement = KtPsiFactory(element.project).createExpression(getReplacementText(changeInfo))
        val elementToReplace = (element.parent as? KtThisExpression) ?: element
        return elementToReplace.replace(newElement).parent as KtElement
    }

    private fun getReplacementText(changeInfo: KotlinChangeInfoBase): String {
        if (changeInfo.receiverParameterInfo != parameterInfo) return parameterInfo.getInheritedName(null)

        val newName = changeInfo.newName
        if (newName.isIdentifier()) return "this@$newName"

        return "this"
    }
}

internal class KotlinContextParameterUsage(
    element: KtSimpleNameExpression,
    private val parameterInfo: KotlinParameterInfo
) : UsageInfo(element), KotlinBaseChangeSignatureUsage {
    override fun processUsage(
        changeInfo: KotlinChangeInfoBase,
        element: KtElement,
        allUsages: Array<out UsageInfo>
    ): KtElement? {
        val elementToReplace = element.parent as? KtCallExpression ?: element
        elementToReplace.qualifyNestedThisExpressions()
        val wrapped =
            KtPsiFactory(element.project).createExpression("with (${parameterInfo.getInheritedName(null)}) {\n${elementToReplace.text}\n}")
        elementToReplace.replace(wrapped)
        return null
    }
}

internal class KotlinNonQualifiedOuterThisUsage(
    element: KtThisExpression,
    private val targetDescriptor: Name
) : UsageInfo(element), KotlinBaseChangeSignatureUsage {
    override fun processUsage(
        changeInfo: KotlinChangeInfoBase,
        element: KtElement,
        allUsages: Array<out UsageInfo>
    ): KtElement {
        val newElement = KtPsiFactory(element.project).createExpression(getReplacementText())
        val elementToReplace = (element.parent as? KtThisExpression) ?: element
        return elementToReplace.replace(newElement).parent as KtElement
    }

    private fun getReplacementText(): String = "this@${targetDescriptor.asString()}"
}

internal class KotlinImplicitThisToParameterUsage(
    callElement: KtElement,
    val parameterInfo: KotlinParameterInfo,
) : UsageInfo(callElement), KotlinBaseChangeSignatureUsage {
    private fun getNewReceiverText(): String = parameterInfo.getInheritedName(null)
    override fun processUsage(
        changeInfo: KotlinChangeInfoBase,
        element: KtElement,
        allUsages: Array<out UsageInfo>
    ): KtElement {
        val parent = element.parent
        if (parent is KtCallExpression) {
            return processUsage(changeInfo, parent, allUsages)
        }
        val newQualifiedCall = KtPsiFactory(element.project).createExpression("${getNewReceiverText()}.${element.text}") as KtQualifiedExpression
        return element.replace(newQualifiedCall) as KtElement
    }
}

internal class KotlinImplicitThisUsage(
    callElement: KtElement,
    private val newReceiver: String
) : UsageInfo(callElement), KotlinBaseChangeSignatureUsage {

    override fun processUsage(
        changeInfo: KotlinChangeInfoBase,
        element: KtElement,
        allUsages: Array<out UsageInfo>
    ): KtElement {
        val parent = element.parent
        if (parent is KtCallExpression) {
            return processUsage(changeInfo, parent, allUsages)
        }
        val newQualifiedCall = KtPsiFactory(element.project).createExpression("$newReceiver.${element.text}"
        ) as KtQualifiedExpression
        return element.replace(newQualifiedCall).parent as KtElement
    }
}

fun PsiElement.qualifyNestedThisExpressions() {
    forEachDescendantOfType<KtThisExpression> { thisExpression ->
        val labelQualifier = thisExpression.labelQualifier
        if (labelQualifier != null) {
            return@forEachDescendantOfType
        }
        val element = thisExpression.instanceReference.mainReference.resolve()
        val label = when (element) {
            is KtClassOrObject -> element.name
            is KtTypeReference -> element.getParentOfType<KtCallableDeclaration>(true)?.name
            is KtFunctionLiteral -> {
                allowAnalysisFromWriteActionInEdt(element) {
                    element.containingKtFile.scopeContext(element).implicitReceivers.firstNotNullOfOrNull {
                        it.ownerSymbol as? KaClassSymbol
                    }?.name?.asString()
                }
            }
            else -> null
        } ?: return@forEachDescendantOfType
        thisExpression.replace(KtPsiFactory(project).createExpression("this@$label"))
        return@forEachDescendantOfType
    }
}