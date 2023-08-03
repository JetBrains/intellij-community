// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.ui

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiCodeFragment
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.types.KtErrorType
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.descriptors.Visibility
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinChangeInfo
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinChangeSignatureProcessor
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinMethodDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinParameterInfo
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinTypeInfo
import org.jetbrains.kotlin.idea.refactoring.changeSignature.KotlinValVar
import org.jetbrains.kotlin.idea.refactoring.changeSignature.ui.KotlinBaseChangePropertySignatureDialog
import org.jetbrains.kotlin.psi.KtCodeFragment
import org.jetbrains.kotlin.psi.KtExpression
import javax.swing.DefaultComboBoxModel

@OptIn(KtAllowAnalysisOnEdt::class)
class KotlinChangePropertySignatureDialog(project: Project,
                                          private val methodDescriptor: KotlinMethodDescriptor) :
    KotlinBaseChangePropertySignatureDialog<KotlinParameterInfo, Visibility, KotlinMethodDescriptor>(project, methodDescriptor) {
    override fun fillVisibilities(model: DefaultComboBoxModel<Visibility>) {
        model.addAll(listOf(Visibilities.Internal, Visibilities.Private, Visibilities.Protected, Visibilities.Public))
    }

    override fun createReturnTypeCodeFragment(m: KotlinMethodDescriptor): KtCodeFragment {
        return kotlinPsiFactory.createExpressionCodeFragment(m.oldReturnType, m.method)
    }

    override fun createReceiverTypeCodeFragment(m: KotlinMethodDescriptor): KtCodeFragment {
        return kotlinPsiFactory.createExpressionCodeFragment(m.oldReceiverType ?: "", m.method)
    }

    override fun isDefaultVisibility(v: Visibility): Boolean {
        return v == Visibilities.Public
    }

    override fun PsiCodeFragment?.isValidType(): Boolean {
        if (this !is KtCodeFragment) return false
        val typeRef = findChildByClass(KtExpression::class.java) ?: return false
        allowAnalysisOnEdt {
            analyze(typeRef) {
                val ktType = typeRef.getKtType()
                return ktType !is KtErrorType
            }
        }
    }

    private fun evaluateKotlinChangeInfo(): KotlinChangeInfo {
        val receiver = if (receiverTypeCheckBox?.isSelected == true) {
            methodDescriptor.receiver ?: KotlinParameterInfo(
                -1,
                KotlinTypeInfo(null, methodDescriptor.method),
                name = "receiver",
                defaultValueForCall = receiverDefaultValueCodeFragment.getContentElement(),
                valOrVar = KotlinValVar.None,
                defaultValue = null,
                defaultValueAsDefaultParameter = false,
                context = methodDescriptor.method
            )
        } else null

        receiver?.setType(receiverTypeCodeFragment.text)
        return KotlinChangeInfo(
            methodDescriptor,
            emptyList(),
            if (methodDescriptor.canChangeVisibility()) visibilityCombo.selectedItem as Visibility else methodDescriptor.visibility,
            receiver,
            name,
            KotlinTypeInfo(returnTypeCodeFragment.text, methodDescriptor.method)
        )
    }

    override fun doAction() {
        val changeInfo = evaluateKotlinChangeInfo()
        changeInfo.receiverParameterInfo?.let {
            val defaultValue = it.defaultValueForCall
            //todo it.defaultValueForCall = AddFullQualifierIntention.Holder.addQualifiersRecursively(defaultValue) as? KtExpression
        }
        invokeRefactoring(KotlinChangeSignatureProcessor(myProject, changeInfo))
    }
}