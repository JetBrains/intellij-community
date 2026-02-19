// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.ui

import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiCodeFragment
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.types.KaErrorType
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.descriptors.Visibility
import org.jetbrains.kotlin.idea.base.analysis.api.utils.analyzeInModalWindow
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.utils.AddQualifiersUtil
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinChangeInfo
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinChangeSignatureProcessor
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinMethodDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinParameterInfo
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinTypeInfo
import org.jetbrains.kotlin.idea.refactoring.changeSignature.KotlinValVar
import org.jetbrains.kotlin.idea.refactoring.changeSignature.ui.KotlinBaseChangePropertySignatureDialog
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtTypeCodeFragment
import org.jetbrains.kotlin.types.Variance
import javax.swing.DefaultComboBoxModel

class KotlinChangePropertySignatureDialog(project: Project,
                                          private val methodDescriptor: KotlinMethodDescriptor) :
    KotlinBaseChangePropertySignatureDialog<KotlinParameterInfo, Visibility, KotlinMethodDescriptor>(project, methodDescriptor) {
    override fun fillVisibilities(model: DefaultComboBoxModel<Visibility>) {
        model.addAll(listOf(Visibilities.Internal, Visibilities.Private, Visibilities.Protected, Visibilities.Public))
    }

    @OptIn(KaExperimentalApi::class)
    override fun createReturnTypeCodeFragment(m: KotlinMethodDescriptor): KtTypeCodeFragment {
        val returnPresentableText =
            analyzeInModalWindow(m.method, KotlinBundle.message("fix.change.signature.prepare")) {
                (m.method as? KtCallableDeclaration)?.returnType?.render(KaTypeRendererForSource.WITH_SHORT_NAMES, position = Variance.INVARIANT)
            }
        return kotlinPsiFactory.createTypeCodeFragment(returnPresentableText ?: "", m.method)
    }

    @OptIn(KaExperimentalApi::class)
    override fun createReceiverTypeCodeFragment(m: KotlinMethodDescriptor): KtTypeCodeFragment {
        val receiverPresentableType =
            analyzeInModalWindow(m.method, KotlinBundle.message("fix.change.signature.prepare")) {
                (m.method as? KtCallableDeclaration)?.receiverTypeReference?.type?.render(KaTypeRendererForSource.WITH_SHORT_NAMES, position = Variance.INVARIANT)
            }
        return kotlinPsiFactory.createTypeCodeFragment(receiverPresentableType ?: "", m.method)
    }

    override fun isDefaultVisibility(v: Visibility): Boolean {
        return v == Visibilities.Public
    }

    override fun PsiCodeFragment?.isValidType(): Boolean {
        if (this !is KtTypeCodeFragment) return false
        val typeRef = getContentElement() ?: return false
        analyze(typeRef) {
            val ktType = typeRef.type
            return ktType !is KaErrorType
        }
    }

    override fun validateButtons() {
        validateButtonsAsync()
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

        if (receiver != null) {
            receiver.setType(receiverTypeCodeFragment.getCanonicalText(false))
        }
        return KotlinChangeInfo(
            methodDescriptor,
            emptyList(),
            if (methodDescriptor.canChangeVisibility()) visibilityCombo.selectedItem as Visibility else methodDescriptor.visibility,
            receiver,
            name,
            KotlinTypeInfo(returnTypeCodeFragment.getCanonicalText(false), methodDescriptor.method)
        )
    }

    override fun doAction() {
        val changeInfo = ActionUtil.underModalProgress(project, KotlinBundle.message("fix.change.signature.prepare")) {
            evaluateKotlinChangeInfo()
        }
        changeInfo.receiverParameterInfo?.let {
            val codeFragment = receiverDefaultValueCodeFragment.getContentElement()
            if (codeFragment != null) {
                it.defaultValue = AddQualifiersUtil.addQualifiersRecursively(codeFragment) as? KtExpression
            }
        }
        invokeRefactoring(KotlinChangeSignatureProcessor(myProject, changeInfo))
    }
}