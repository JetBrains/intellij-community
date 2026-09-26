// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinChangeInfo
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinChangeSignatureProcessor
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinMethodDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix.ReceiverParameterChangeSignatureUtils.collectUsedTypeParameters
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix.ReceiverParameterChangeSignatureUtils.isReceiverUsedInside
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

object CompanionBlockMemberExtensionFixFactory {
    val companionBlockMemberExtension: KotlinQuickFixFactory.IntentionBased<KaFirDiagnostic.CompanionBlockMemberExtension> =
        KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.CompanionBlockMemberExtension ->
            val function = diagnostic.psi as? KtNamedFunction
                ?: diagnostic.psi.getStrictParentOfType<KtNamedFunction>()
                ?: return@IntentionBased emptyList()
            val receiverTypeReference = function.receiverTypeReference ?: return@IntentionBased emptyList()

            val isReceiverUsed = analyze(function) {
                val usedReifiedTypeParametersInReceiver =
                    collectUsedTypeParameters(receiverTypeReference).filterTo(mutableSetOf()) { it.isReified }
                isReceiverUsedInside(function, usedReifiedTypeParametersInReceiver)
            }

            buildList<KotlinQuickFixAction<KtNamedFunction>> {
                if (isReceiverUsed) {
                    add(ConvertReceiverToParameterFix(function))
                    if (receiverTypeReference.languageVersionSettings.supportsFeature(LanguageFeature.ContextParameters)) {
                        add(ConvertReceiverToContextParameterFix(function))
                    }
                } else {
                    add(RemoveReceiverParameterFix(function))
                }
            }
        }

    private class ConvertReceiverToParameterFix(
        function: KtNamedFunction,
    ) : KotlinQuickFixAction<KtNamedFunction>(function) {
        override fun invoke(project: Project, editor: Editor?, file: KtFile) {
            val function = element ?: return
            val changeInfo = function.toCreateChangeInfo()
            changeInfo.receiverParameterInfo = null
            KotlinChangeSignatureProcessor(project, changeInfo).run()
        }

        override fun startInWriteAction(): Boolean = false
        override fun getText(): String = familyName
        override fun getFamilyName(): String = KotlinBundle.message("convert.receiver.to.parameter")
    }

    private class ConvertReceiverToContextParameterFix(
        function: KtNamedFunction,
    ) : KotlinQuickFixAction<KtNamedFunction>(function) {
        override fun invoke(project: Project, editor: Editor?, file: KtFile) {
            val function = element ?: return
            val changeInfo = function.toCreateChangeInfo()
            val oldReceiverInfo = changeInfo.oldReceiverInfo ?: return
            oldReceiverInfo.isContextParameter = true
            changeInfo.receiverParameterInfo = null
            KotlinChangeSignatureProcessor(project, changeInfo).also {
                it.prepareSuccessfulSwingThreadCallback = Runnable { }
            }.run()
        }

        override fun startInWriteAction(): Boolean = false
        override fun getText(): String = familyName
        override fun getFamilyName(): String = KotlinBundle.message("convert.receiver.parameter.to.context.parameter")
    }

    private fun KtNamedFunction.toCreateChangeInfo(): KotlinChangeInfo =
        KotlinChangeInfo(KotlinMethodDescriptor(this))
}
