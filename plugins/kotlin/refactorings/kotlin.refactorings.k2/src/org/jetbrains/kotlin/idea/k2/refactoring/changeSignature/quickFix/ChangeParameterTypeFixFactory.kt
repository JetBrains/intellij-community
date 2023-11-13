// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.calls.KtErrorCallInfo
import org.jetbrains.kotlin.analysis.api.calls.KtFunctionCall
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KtTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.symbols.KtConstructorSymbol
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.analysis.api.types.KtTypeNullability
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.diagnosticFixFactory
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinChangeInfo
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinChangeSignatureProcessor
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinMethodDescriptor
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.types.Variance


object ChangeParameterTypeFixFactory {
    val typeMismatchFactory = diagnosticFixFactory(KtFirDiagnostic.ArgumentTypeMismatch::class) { diagnostic ->
        val psi = diagnostic.psi
        val targetType = diagnostic.actualType
        createTypeMismatchFixes(psi, targetType)
    }

    val nullForNotNullTypeFactory = diagnosticFixFactory(KtFirDiagnostic.NullForNonnullType::class) { diagnostic ->
       createTypeMismatchFixes(diagnostic.psi, diagnostic.expectedType.withNullability(KtTypeNullability.NULLABLE))
    }

    context(KtAnalysisSession)
    private fun createTypeMismatchFixes(psi: PsiElement, targetType: KtType): List<KotlinQuickFixAction<*>> {
        val valueArgument = psi.parent as? KtValueArgument ?: return emptyList()

        val callElement = valueArgument.parentOfType<KtCallElement>() ?: return emptyList()
        val memberCall = (callElement.resolveCall() as? KtErrorCallInfo)?.candidateCalls?.firstOrNull() as? KtFunctionCall<*>
        val functionLikeSymbol = memberCall?.symbol ?: return emptyList()

        val paramSymbol = memberCall.argumentMapping[valueArgument.getArgumentExpression()]
        val parameter = paramSymbol?.symbol?.psi as? KtParameter ?: return emptyList()

        val functionName = getDeclarationName(functionLikeSymbol) ?: return emptyList()

        return listOf(ChangeParameterTypeFix(
            parameter,
            targetType,
            functionLikeSymbol is KtConstructorSymbol && functionLikeSymbol.isPrimary,
            functionName
        ))
    }
}

context(KtAnalysisSession)
internal class ChangeParameterTypeFix(
    element: KtParameter,
    type: KtType,
    private val isPrimaryConstructorParameter: Boolean,
    private val functionName: String
) : KotlinQuickFixAction<KtParameter>(element) {
    private val approximatedType = type.approximateToSuperPublicDenotableOrSelf(true)

    private val typePresentation = approximatedType.render(KtTypeRendererForSource.WITH_SHORT_NAMES, position = Variance.IN_VARIANCE)
    private val typeFQNPresentation = approximatedType.render(position = Variance.IN_VARIANCE)

    override fun startInWriteAction(): Boolean = false

    override fun getText(): String = element?.let {
        when {
            isPrimaryConstructorParameter -> {
                KotlinBundle.message(
                    "fix.change.return.type.text.primary.constructor",
                    it.name.toString(), functionName, typePresentation
                )
            }
            else -> {
                KotlinBundle.message(
                    "fix.change.return.type.text.function",
                    it.name.toString(), functionName, typePresentation
                )
            }
        }
    } ?: ""

    override fun getFamilyName() = KotlinBundle.message("fix.change.return.type.family")

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val element = element ?: return
        val function = element.ownerFunction as? KtFunction ?: return

        val parameterIndex = function.valueParameters.indexOf(element)

        val methodDescriptor = KotlinMethodDescriptor(function)

        val changeInfo = KotlinChangeInfo(methodDescriptor)
        val parameterInfo = changeInfo.newParameters[if (methodDescriptor.receiver != null) parameterIndex + 1 else parameterIndex]

        parameterInfo.setType(typeFQNPresentation)

        KotlinChangeSignatureProcessor(project, changeInfo).run()
    }
}