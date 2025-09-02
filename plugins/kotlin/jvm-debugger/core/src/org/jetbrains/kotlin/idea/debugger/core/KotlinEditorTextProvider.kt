// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.core

import com.intellij.debugger.engine.evaluation.CodeFragmentKind
import com.intellij.debugger.engine.evaluation.TextWithImports
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl
import com.intellij.debugger.impl.EditorTextProvider
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.parents
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.resolution.*
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.util.registryFlag
import org.jetbrains.kotlin.idea.debugger.base.util.runDumbAnalyze
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
            is KtBinaryExpressionWithTypeRHS, is KtIsExpression, is KtDoubleColonExpression, is KtThisExpression -> true
            is LeafPsiElement -> target.elementType == KtTokens.IDENTIFIER
            is KtReferenceExpression -> isReferenceAllowed(target, allowMethodCalls)
            is KtOperationExpression -> {
                isReferenceAllowed(target.operationReference, allowMethodCalls) &&
                        allowMethodCalls // TODO: check operation arguments: allowMethodCalls || arguments.all { it.isSafe }
            }
            is KtQualifiedExpression -> {
                val selector = target.selectorExpression
                selector is KtReferenceExpression && isReferenceAllowed(selector, allowMethodCalls)
            }
            else -> allowMethodCalls
        }

        return if (isAllowed) target else findEvaluationTarget(target.parent, allowMethodCalls)
    }

    private tailrec fun calculateCandidate(originalElement: PsiElement?, allowMethodCalls: Boolean): PsiElement? {
        val candidate = originalElement?.getParentOfType<KtExpression>(strict = false) ?: return null

        if (!isAcceptedAsCandidate(candidate)) {
            return null
        }

        return when (candidate) {
            is KtParameter -> if (originalElement == candidate.nameIdentifier) candidate.nameIdentifier else null
            is KtVariableDeclaration -> if (originalElement == candidate.nameIdentifier) candidate.nameIdentifier else null
            is KtObjectDeclaration -> if (candidate.isObjectLiteral()) candidate else null
            is KtDeclaration, is KtFile -> null
            is KtIfExpression -> if (candidate.`else` != null) candidate else null
            is KtStatementExpression, is KtLabeledExpression, is KtLabelReferenceExpression -> null
            is KtStringTemplateExpression -> if (isStringTemplateAllowed(candidate, allowMethodCalls)) candidate else null
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

    private fun isReferenceAllowed(reference: KtReferenceExpression, allowMethodCalls: Boolean): Boolean = runDumbAnalyze(reference, fallback = false) f@ {
        when {
            reference is KtBinaryExpressionWithTypeRHS -> return@f true
            reference is KtOperationReferenceExpression && reference.operationSignTokenType == KtTokens.ELVIS -> return@f true
            reference is KtCollectionLiteralExpression -> return@f false
            reference is KtCallExpression -> {
                val callInfo = reference.resolveToCall() as? KaSuccessCallInfo ?: return@f false

                return@f when (val call = callInfo.call) {
                    is KaAnnotationCall -> {
                        val languageVersionSettings = reference.languageVersionSettings
                        languageVersionSettings.supportsFeature(LanguageFeature.InstantiationOfAnnotationClasses)
                    }
                    is KaFunctionCall<*> -> {
                        val functionSymbol = call.partiallyAppliedSymbol.symbol
                        isSymbolAllowed(functionSymbol, allowMethodCalls)
                    }
                    is KaCompoundVariableAccessCall -> {
                        val functionSymbol = call.compoundOperation.operationPartiallyAppliedSymbol.symbol
                        isSymbolAllowed(functionSymbol, allowMethodCalls)
                    }
                    else -> false
                }
            }
            else -> {
                val symbol = reference.mainReference.resolveToSymbol()
                return@f symbol == null || isSymbolAllowed(symbol, allowMethodCalls)
            }
        }
    }

    @OptIn(KaExperimentalApi::class) // for KaContextParameterSymbol
    private fun isSymbolAllowed(symbol: KaSymbol, allowMethodCalls: Boolean): Boolean {
        return when (symbol) {
            is KaClassSymbol -> symbol.classKind.isObject
            is KaFunctionSymbol -> allowMethodCalls
            is KaPropertySymbol, is KaJavaFieldSymbol, is KaLocalVariableSymbol, is KaValueParameterSymbol, is KaContextParameterSymbol, is KaEnumEntrySymbol -> true
            else -> false
        }
    }

    private fun isStringTemplateAllowed(candidate: KtStringTemplateExpression, allowMethodCalls: Boolean) =
        !candidate.text.startsWith("\"\"\"") || allowMethodCalls


    private val FORBIDDEN_PARENT_TYPES = setOf(
        KtUserType::class.java,
        KtImportDirective::class.java,
        KtPackageDirective::class.java,
        KtValueArgumentName::class.java,
        KtTypeAlias::class.java,
        KtAnnotationEntry::class.java,
        KtDeclarationModifierList::class.java
    )

    private val NOT_FORBIDDEN_CANDIDATE_PARENT_TYPES = setOf(
        KtContextReceiverList::class.java, // it's located inside KtDeclarationModifierList, so should be allowed explicitly
    )

    private fun isAcceptedAsCandidate(element: PsiElement): Boolean {
        return isAccepted(element, FORBIDDEN_PARENT_TYPES, NOT_FORBIDDEN_CANDIDATE_PARENT_TYPES)
    }

    override fun isAcceptedAsCodeFragmentContext(element: PsiElement): Boolean {
        return isAccepted(element, FORBIDDEN_PARENT_TYPES, emptySet())
    }

    private fun isAccepted(
        element: PsiElement,
        forbiddenTypes: Set<Class<*>>,
        notForbiddenTypes: Set<Class<*>>,
    ): Boolean {
        for (parent in element.parents(withSelf = true)) {
            val cls = parent::class.java
            if (cls in notForbiddenTypes) {
                return true
            }
            if (cls in forbiddenTypes) {
                return false
            }
        }

        return true
    }
}