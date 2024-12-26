// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.KaErrorCallInfo
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.symbols.KaConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.analysis.api.types.KaDefinitelyNotNullType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeNullability
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinChangeInfo
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinChangeSignatureProcessor
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinMethodDescriptor
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.types.Variance

object ChangeParameterTypeFixFactory {

    val typeMismatchFactory = KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.ArgumentTypeMismatch ->
        val psi = diagnostic.psi
        val targetType = diagnostic.expectedType
        buildList {
            if (targetType is KaDefinitelyNotNullType) {
                addAll(createTypeMismatchFixesForDefinitelyNonNullable(psi, targetType))
            }
            // Here we change the type of the value argument to the type the function/constructor is called with (actualType)
            addAll(createTypeMismatchFixes(psi, diagnostic.actualType))
        }
    }

    val nullForNotNullTypeFactory = KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.NullForNonnullType ->
        createTypeMismatchFixes(diagnostic.psi, diagnostic.expectedType.withNullability(KaTypeNullability.NULLABLE))
    }

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    private fun createTypeMismatchFixes(psi: PsiElement, targetType: KaType): List<KotlinQuickFixAction<*>> {
        val psiParent = psi.parent ?: return emptyList()
        // Support of overloaded operators and anonymous objects infix calls
        val (argumentKey, callElement) = if (psiParent is KtOperationExpression) {
            Pair(psi, psiParent)
        } else {
            val valueArgument = getValueArgument(psi) ?: return emptyList()
            val valueArgumentExpression = valueArgument.getArgumentExpression() ?: return emptyList()
            val argumentKey = if (valueArgumentExpression is KtParenthesizedExpression) {
                psi
            } else {
                valueArgumentExpression
            }
            val callElement = valueArgument.parentOfType<KtCallElement>() ?: return emptyList()
            Pair(argumentKey, callElement)
        }
        val memberCall = (callElement.resolveToCall() as? KaErrorCallInfo)?.candidateCalls?.firstOrNull() as? KaFunctionCall<*>
        val functionLikeSymbol = memberCall?.symbol ?: return emptyList()

        val paramSymbol = memberCall.argumentMapping[argumentKey]
        val parameter = paramSymbol?.symbol?.psi as? KtParameter ?: return emptyList()

        createChangeParameterTypeFix(parameter, targetType, functionLikeSymbol)?.let { fix -> return listOf(fix) } ?: return emptyList()
    }

    context(KaSession)
    private fun createTypeMismatchFixesForDefinitelyNonNullable(
        psi: PsiElement,
        targetType: KaDefinitelyNotNullType
    ): List<KotlinQuickFixAction<*>> {
        val valueArgument = getValueArgument(psi) ?: return emptyList()

        val argumentExpression = valueArgument.getArgumentExpression()?.let { KtPsiUtil.deparenthesize(it) } ?: return emptyList()
        val argumentOrSelectorExpression = if (argumentExpression is KtDotQualifiedExpression) {
            argumentExpression.selectorExpression
        } else {
            argumentExpression
        }
        val referencedSymbol = argumentOrSelectorExpression?.mainReference?.resolveToSymbol()?.let { symbol ->
            if (symbol is KaPropertySymbol) {
                getValueParameterSymbolForPropertySymbol(symbol)
            } else {
                symbol
            }
        }
        if (referencedSymbol !is KaValueParameterSymbol) return emptyList()

        val containingFunctionSymbol = referencedSymbol.containingDeclaration as? KaFunctionSymbol ?: return emptyList()
        val parameter = referencedSymbol.psi as? KtParameter ?: return emptyList()

        createChangeParameterTypeFix(parameter, targetType, containingFunctionSymbol)?.let { fix -> return listOf(fix) }
            ?: return emptyList()
    }

    private fun getValueArgument(psi: PsiElement): KtValueArgument? {
        val psiParent = psi.parent
        val neededParent = if (psiParent is KtParenthesizedExpression) {
            psiParent.parent
        } else {
            psiParent
        }
        return neededParent as? KtValueArgument
    }

    context(KaSession)
    private fun getValueParameterSymbolForPropertySymbol(propertySymbol: KaPropertySymbol): KaValueParameterSymbol? {
        val probableConstructorParameterPsi = propertySymbol.psi as? KtParameter
        return probableConstructorParameterPsi?.symbol as? KaValueParameterSymbol
    }

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    private fun createChangeParameterTypeFix(
        parameter: KtParameter,
        targetType: KaType,
        functionLikeSymbol: KaFunctionSymbol
    ): ChangeParameterTypeFix? {
        val isPrimaryConstructorParameter = functionLikeSymbol is KaConstructorSymbol && functionLikeSymbol.isPrimary
        val functionName = getDeclarationName(functionLikeSymbol) ?: return null

        val approximatedType = targetType.approximateToSuperPublicDenotableOrSelf(true)
        val typePresentation = approximatedType.render(KaTypeRendererForSource.WITH_SHORT_NAMES, position = Variance.IN_VARIANCE)
        val typeFQNPresentation = approximatedType.render(position = Variance.IN_VARIANCE)

        return ChangeParameterTypeFix(
            parameter,
            typePresentation,
            typeFQNPresentation,
            isPrimaryConstructorParameter,
            functionName
        )
    }
}

internal class ChangeParameterTypeFix(
    element: KtParameter,
    val typePresentation: String,
    val typeFQNPresentation: String,
    private val isPrimaryConstructorParameter: Boolean,
    private val functionName: String
) : KotlinQuickFixAction<KtParameter>(element) {

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