// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.core

import com.intellij.debugger.engine.evaluation.CodeFragmentKind
import com.intellij.debugger.engine.evaluation.TextWithImports
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl
import com.intellij.debugger.impl.EditorTextProvider
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.calls.*
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.util.registryFlag
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

interface KotlinEditorTextProvider : EditorTextProvider {
    fun findEvaluationTarget(originalElement: PsiElement, allowMethodCalls: Boolean): PsiElement?
    fun isAcceptedAsCodeFragmentContext(element: PsiElement): Boolean

    companion object {
        val instance: KotlinEditorTextProvider
            @ApiStatus.Internal get() = EditorTextProvider.EP.forLanguage(KotlinLanguage.INSTANCE) as KotlinEditorTextProvider
    }
}

@TestOnly
@ApiStatus.Internal
fun <T> KotlinEditorTextProvider.Companion.withCustomConfiguration(useAnalysisApi: Boolean, block: (KotlinEditorTextProvider) -> T): T {
    val instance = instance as EnclosingKotlinEditorTextProvider

    val oldValue = instance.useAnalysisApi
    try {
        instance.useAnalysisApi = useAnalysisApi
        return block(instance)
    } finally {
        instance.useAnalysisApi = oldValue
    }
}

internal class EnclosingKotlinEditorTextProvider : KotlinEditorTextProvider {
    internal var useAnalysisApi: Boolean by registryFlag("debugger.kotlin.analysis.api.editor.text.provider")
        @TestOnly set

    private val implementation: KotlinEditorTextProvider
        get() {
            if (!useAnalysisApi) {
                KotlinDebuggerLegacyFacade.getInstance()?.editorTextProvider?.let { return it }
            }

            return AnalysisApiBasedKotlinEditorTextProvider
        }

    override fun getEditorText(elementAtCaret: PsiElement): TextWithImports? =
        implementation.getEditorText(elementAtCaret)

    override fun findExpression(elementAtCaret: PsiElement, allowMethodCalls: Boolean): Pair<PsiElement, TextRange>? =
        implementation.findExpression(elementAtCaret, allowMethodCalls)

    override fun findEvaluationTarget(originalElement: PsiElement, allowMethodCalls: Boolean): PsiElement? =
        implementation.findEvaluationTarget(originalElement, allowMethodCalls)

    override fun isAcceptedAsCodeFragmentContext(element: PsiElement): Boolean =
        implementation.isAcceptedAsCodeFragmentContext(element)
}

private object AnalysisApiBasedKotlinEditorTextProvider : KotlinEditorTextProvider {
    override fun getEditorText(elementAtCaret: PsiElement): TextWithImports? {
        val expression = findEvaluationTarget(elementAtCaret, true) ?: return null
        return TextWithImportsImpl(CodeFragmentKind.EXPRESSION, expression.text, "", KotlinFileType.INSTANCE)
    }

    override fun findExpression(elementAtCaret: PsiElement, allowMethodCalls: Boolean): Pair<PsiElement, TextRange>? {
        val expression = findEvaluationTarget(elementAtCaret, allowMethodCalls) ?: return null
        return Pair(expression, expression.textRange)
    }

    override tailrec fun findEvaluationTarget(originalElement: PsiElement, allowMethodCalls: Boolean): PsiElement? {
        val candidate = calculateCandidate(originalElement, allowMethodCalls) ?: return null

        val target = when (candidate) {
            is KtParameter, is KtVariableDeclaration -> originalElement
            else -> candidate
        }

        val isAllowed = when (target) {
            is KtBinaryExpressionWithTypeRHS, is KtIsExpression -> true
            is KtOperationExpression -> isReferenceAllowed(target.operationReference, allowMethodCalls)
            is KtReferenceExpression -> isReferenceAllowed(target, allowMethodCalls)
            is KtQualifiedExpression -> {
                val selector = target.selectorExpression
                selector is KtReferenceExpression && isReferenceAllowed(selector, allowMethodCalls)
            }
            else -> true
        }

        return if (isAllowed) target else findEvaluationTarget(target.parent, allowMethodCalls)
    }

    private tailrec fun calculateCandidate(originalElement: PsiElement?, allowMethodCalls: Boolean): PsiElement? {
        val candidate = originalElement?.getParentOfType<KtExpression>(strict = false) ?: return null

        if (!isAcceptedAsCodeFragmentContext(candidate)) {
            return null
        }

        return when (candidate) {
            is KtParameter -> if (originalElement == candidate.nameIdentifier) candidate.nameIdentifier else null
            is KtVariableDeclaration -> if (originalElement == candidate.nameIdentifier) candidate.nameIdentifier else null
            is KtObjectDeclaration -> if (candidate.isObjectLiteral()) candidate else null
            is KtDeclaration, is KtFile -> null
            is KtIfExpression -> if (candidate.`else` != null) candidate else null
            is KtStatementExpression, is KtLabeledExpression -> null
            is KtConstantExpression -> calculateCandidate(candidate.parent, allowMethodCalls)
            else -> when (val parent = candidate.parent) {
                is KtThisExpression -> parent
                is KtSuperExpression -> calculateCandidate(parent.parent, allowMethodCalls)
                is KtCallExpression -> calculateCandidate(parent, allowMethodCalls)
                is KtArrayAccessExpression -> when (parent.arrayExpression) {
                    candidate -> candidate
                    else -> calculateCandidate(parent, allowMethodCalls)
                }
                is KtQualifiedExpression -> when (parent.receiverExpression) {
                    candidate -> candidate
                    else -> calculateCandidate(parent, allowMethodCalls)
                }
                is KtOperationExpression -> if (parent.operationReference == candidate) parent else candidate
                is KtCallableReferenceExpression -> if (parent.callableReference == candidate) parent else candidate
                else -> candidate
            }
        }
    }

    private fun isReferenceAllowed(reference: KtReferenceExpression, allowMethodCalls: Boolean): Boolean = analyze(reference) {
        when {
            reference is KtBinaryExpressionWithTypeRHS -> return true
            reference is KtOperationReferenceExpression && reference.operationSignTokenType == KtTokens.ELVIS -> return true
            reference is KtCollectionLiteralExpression -> return false
            reference is KtCallExpression -> {
                val callInfo = reference.resolveCall() as? KtSuccessCallInfo ?: return false

                return when (val call = callInfo.call) {
                    is KtAnnotationCall -> {
                        val languageVersionSettings = reference.languageVersionSettings
                        languageVersionSettings.supportsFeature(LanguageFeature.InstantiationOfAnnotationClasses)
                    }
                    is KtFunctionCall<*> -> {
                        val functionSymbol = call.partiallyAppliedSymbol.symbol
                        isSymbolAllowed(functionSymbol, allowMethodCalls)
                    }
                    is KtCompoundVariableAccessCall -> {
                        val functionSymbol = call.compoundAccess.operationPartiallyAppliedSymbol.symbol
                        isSymbolAllowed(functionSymbol, allowMethodCalls)
                    }
                    else -> false
                }
            }
            else -> {
                val symbol = reference.mainReference.resolveToSymbol()
                return symbol == null || isSymbolAllowed(symbol, allowMethodCalls)
            }
        }
    }

    private fun isSymbolAllowed(symbol: KtSymbol, allowMethodCalls: Boolean): Boolean {
        return when (symbol) {
            is KtClassOrObjectSymbol -> symbol.classKind.isObject
            is KtFunctionLikeSymbol -> allowMethodCalls
            is KtVariableSymbol, is KtValueParameterSymbol, is KtEnumEntrySymbol -> true
            else -> false
        }
    }

    private val NOT_ACCEPTED_AS_CONTEXT_TYPES = arrayOf(
        KtUserType::class.java,
        KtImportDirective::class.java,
        KtPackageDirective::class.java,
        KtValueArgumentName::class.java,
        KtTypeAlias::class.java,
        KtAnnotationEntry::class.java
    )

    override fun isAcceptedAsCodeFragmentContext(element: PsiElement): Boolean {
        return !NOT_ACCEPTED_AS_CONTEXT_TYPES.contains(element::class.java as Class<*>)
                && PsiTreeUtil.getParentOfType(element, *NOT_ACCEPTED_AS_CONTEXT_TYPES) == null
    }
}