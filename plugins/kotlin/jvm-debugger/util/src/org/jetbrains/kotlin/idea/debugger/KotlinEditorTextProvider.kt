// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger

import com.intellij.debugger.engine.evaluation.CodeFragmentKind
import com.intellij.debugger.engine.evaluation.TextWithImports
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl
import com.intellij.debugger.impl.EditorTextProvider
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.calls.*
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.util.registryFlag
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorUtils

private interface KotlinAwareEditorTextProvider : EditorTextProvider {
    fun findEvaluationTarget(originalElement: PsiElement, allowMethodCalls: Boolean): PsiElement?
    fun isAcceptedAsCodeFragmentContext(element: PsiElement): Boolean
}

object KotlinEditorTextProvider : KotlinAwareEditorTextProvider {
    var useAnalysisApi: Boolean by registryFlag("debugger.kotlin.analysis.api.editor.text.provider")
        @TestOnly set

    private val provider: KotlinAwareEditorTextProvider
        get() = when {
            useAnalysisApi -> AnalysisApiBasedKotlinEditorTextProvider
            else -> LegacyApiBasedKotlinEditorTextProvider
        }

    override fun getEditorText(elementAtCaret: PsiElement): TextWithImports? =
        provider.getEditorText(elementAtCaret)

    override fun findExpression(elementAtCaret: PsiElement, allowMethodCalls: Boolean): Pair<PsiElement, TextRange>? =
        provider.findExpression(elementAtCaret, allowMethodCalls)

    override fun findEvaluationTarget(originalElement: PsiElement, allowMethodCalls: Boolean): PsiElement? =
        provider.findEvaluationTarget(originalElement, allowMethodCalls)

    override fun isAcceptedAsCodeFragmentContext(element: PsiElement): Boolean =
        provider.isAcceptedAsCodeFragmentContext(element)
}

private object AnalysisApiBasedKotlinEditorTextProvider : KotlinAwareEditorTextProvider {
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

private object LegacyApiBasedKotlinEditorTextProvider : KotlinAwareEditorTextProvider {
    override fun getEditorText(elementAtCaret: PsiElement): TextWithImports? {
        val expression = findExpressionInner(elementAtCaret, true) ?: return null

        val expressionText = getElementInfo(expression) { it.text }
        return TextWithImportsImpl(CodeFragmentKind.EXPRESSION, expressionText, "", KotlinFileType.INSTANCE)
    }

    override fun findExpression(elementAtCaret: PsiElement, allowMethodCalls: Boolean): Pair<PsiElement, TextRange>? {
        val expression = findExpressionInner(elementAtCaret, allowMethodCalls) ?: return null

        val expressionRange = getElementInfo(expression) { it.textRange }
        return Pair(expression, expressionRange)
    }

    override fun findEvaluationTarget(originalElement: PsiElement, allowMethodCalls: Boolean): PsiElement? {
        return findExpressionInner(originalElement, allowMethodCalls)
    }

    private fun <T> getElementInfo(expr: KtExpression, f: (PsiElement) -> T): T {
        var expressionText = f(expr)

        val nameIdentifier = expr.getNameIdentifier()
        if (nameIdentifier != null) {
            expressionText = f(nameIdentifier)
        }

        return expressionText
    }

    private fun findExpressionInner(element: PsiElement, allowMethodCalls: Boolean): KtExpression? {
        if (!isAcceptedAsCodeFragmentContext(element)) return null

        val ktElement = PsiTreeUtil.getParentOfType(element, KtElement::class.java) ?: return null

        val nameIdentifier = ktElement.getNameIdentifier()
        if (nameIdentifier == element) {
            return ktElement as? KtExpression
        }

        fun KtExpression.qualifiedParentOrSelf(isSelector: Boolean = true): KtExpression {
            val parent = parent
            return if (parent is KtQualifiedExpression && (!isSelector || parent.selectorExpression == this)) parent else this
        }

        val newExpression = when (val parent = ktElement.parent) {
            is KtThisExpression -> parent
            is KtSuperExpression -> parent.qualifiedParentOrSelf(isSelector = false)
            is KtArrayAccessExpression -> if (parent.arrayExpression == ktElement) ktElement else parent.qualifiedParentOrSelf()
            is KtReferenceExpression -> parent.qualifiedParentOrSelf()
            is KtQualifiedExpression -> if (parent.receiverExpression != ktElement) parent else null
            is KtOperationExpression -> if (parent.operationReference == ktElement) parent else null
            else -> null
        }

        if (!allowMethodCalls && newExpression != null) {
            fun PsiElement.isCall() = this is KtCallExpression || this is KtOperationExpression || this is KtArrayAccessExpression

            if (newExpression.isCall() || newExpression is KtQualifiedExpression && newExpression.selectorExpression!!.isCall()) {
                return null
            }
        }

        return when {
            newExpression is KtExpression -> newExpression
            ktElement is KtSimpleNameExpression -> {
                val context = ktElement.analyze()
                val qualifier = context[BindingContext.QUALIFIER, ktElement]
                if (qualifier != null && !DescriptorUtils.isObject(qualifier.descriptor)) {
                    null
                } else {
                    ktElement
                }
            }
            else -> null
        }

    }

    private fun KtElement.getNameIdentifier() =
        if (this is KtProperty || this is KtParameter)
            (this as PsiNameIdentifierOwner).nameIdentifier
        else
            null


    private val NOT_ACCEPTED_AS_CONTEXT_TYPES = arrayOf(
        KtUserType::class.java,
        KtImportDirective::class.java,
        KtPackageDirective::class.java,
        KtValueArgumentName::class.java
    )

    override fun isAcceptedAsCodeFragmentContext(element: PsiElement): Boolean =
        !NOT_ACCEPTED_AS_CONTEXT_TYPES.contains(element::class.java as Class<*>) &&
                PsiTreeUtil.getParentOfType(element, *NOT_ACCEPTED_AS_CONTEXT_TYPES) == null
}