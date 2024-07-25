// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.introduce

import com.intellij.psi.PsiNamedElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.elementType
import com.intellij.psi.util.startOffset
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.resolution.*
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaNamedSymbol
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeParameterType
import org.jetbrains.kotlin.idea.base.analysis.api.utils.CallParameterInfoProvider.getArgumentOrIndexExpressions
import org.jetbrains.kotlin.idea.base.analysis.api.utils.CallParameterInfoProvider.mapArgumentsToParameterIndices
import org.jetbrains.kotlin.idea.base.psi.isInsideKtTypeReference
import org.jetbrains.kotlin.idea.base.psi.safeDeparenthesize
import org.jetbrains.kotlin.idea.base.psi.unifier.KotlinPsiRange
import org.jetbrains.kotlin.idea.base.psi.unifier.KotlinPsiUnificationResult.StrictSuccess
import org.jetbrains.kotlin.idea.base.psi.unifier.KotlinPsiUnificationResult.Success
import org.jetbrains.kotlin.idea.base.psi.unifier.toRange
import org.jetbrains.kotlin.idea.codeinsight.utils.findRelevantLoopForExpression
import org.jetbrains.kotlin.idea.refactoring.introduce.extractableSubstringInfo
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector
import org.jetbrains.kotlin.resolve.calls.util.getCalleeExpressionIfAny
import org.jetbrains.kotlin.utils.addToStdlib.zipWithNulls

object K2SemanticMatcher {
    context(KaSession)
    fun findMatches(patternElement: KtElement, scopeElement: KtElement): List<KtElement> {
        val matches = mutableListOf<KtElement>()

        val substringInfo = (patternElement as? KtExpression)?.extractableSubstringInfo as? K2ExtractableSubstringInfo

        scopeElement.accept(
            object : KtTreeVisitorVoid() {
                override fun visitKtElement(element: KtElement) {
                    when {
                        element == substringInfo?.template -> matches.add(patternElement)
                        element is KtStringTemplateExpression && substringInfo != null -> {
                            val extractableSubstringInfo = getMatchedStringFragmentsOrNull(element, substringInfo, MatchingContext())
                            when {
                                extractableSubstringInfo != null -> {
                                    matches.add(extractableSubstringInfo.createExpression())
                                }

                                else -> {

                                    super.visitKtElement(element)
                                }
                            }
                        }

                        element.isSemanticMatch(patternElement) -> {
                            matches.add(element)
                        }

                        else -> {
                            super.visitKtElement(element)
                        }
                    }
                }
            }
        )

        return matches
    }

    /**
     * Matches the elements in the target KotlinPsiRange with the elements in the pattern KotlinPsiRange, searching for [parameters] replacement.
     *
     * @param target The target KotlinPsiRange to match against.
     * @param pattern The pattern KotlinPsiRange to match.
     * @param parameters The list of parameters which are used in [pattern].
     * @return null if match fails
     */
    context(KaSession)
    fun matchRanges(
        target: KotlinPsiRange,
        pattern: KotlinPsiRange,
        parameters: List<PsiNamedElement>
    ): Success<PsiNamedElement>? {
        if (target.elements.size != pattern.elements.size) return null

        val substitution = mutableMapOf<PsiNamedElement, KtElement?>().apply {
            parameters.forEach {
                put(it, null)
            }
        }

        val matchingContext = MatchingContext(parameterSubstitution = substitution)

        fun prepareResult(
            target: KotlinPsiRange,
            substitution: MutableMap<PsiNamedElement, KtElement?>
        ): StrictSuccess<PsiNamedElement>? {
            val result = mutableMapOf<PsiNamedElement, KtElement>()
            substitution.entries.forEach { (parameter, argument) ->
                if (argument == null) {
                    return null
                }
                result.put(parameter, argument)
            }
            return StrictSuccess(target, result)
        }

        target.elements.zip(pattern.elements) { t, p ->
            if (p !is KtElement) return@zip

            val substringInfo = (p as? KtExpression)?.extractableSubstringInfo as? K2ExtractableSubstringInfo

            if (t is KtStringTemplateExpression && substringInfo != null) {
                val extractableSubstringInfo = getMatchedStringFragmentsOrNull(t, substringInfo, matchingContext)
                if (extractableSubstringInfo != null) {
                    return prepareResult(extractableSubstringInfo.createExpression().toRange(), substitution)
                } else {
                    return null
                }
            }

            if (((t as? KtElement)?.isSemanticMatch(p, matchingContext) != true)) return null
        }

        return prepareResult(target, substitution)
    }

    context(KaSession)
    fun KtElement.isSemanticMatch(patternElement: KtElement): Boolean = isSemanticMatch(patternElement, MatchingContext())

    context(KaSession)
    private fun KtElement.isSemanticMatch(
        patternElement: KtElement,
        context: MatchingContext,
    ): Boolean = this == patternElement || accept(VisitingMatcher(this@KaSession, context), patternElement)

    context(KaSession)
    private fun getMatchedStringFragmentsOrNull(
        target: KtStringTemplateExpression, patternInfo: K2ExtractableSubstringInfo, matchingContext: MatchingContext
    ): K2ExtractableSubstringInfo? {
        val prefixLength = patternInfo.prefix.length
        val suffixLength = patternInfo.suffix.length
        val targetEntries = target.entries
        val patternEntries = patternInfo.entries.toList()
        for ((index, targetEntry) in targetEntries.withIndex()) {
            if (index + patternEntries.size > targetEntries.size) return null

            val targetEntryText = targetEntry.text

            if (patternInfo.startEntry == patternInfo.endEntry && (prefixLength > 0 || suffixLength > 0)) {
                if (targetEntry !is KtLiteralStringTemplateEntry) continue

                val patternText = with(patternInfo.startEntry.text) { substring(prefixLength, length - suffixLength) }
                val i = targetEntryText.indexOf(patternText)
                if (i < 0) continue
                val targetPrefix = targetEntryText.substring(0, i)
                val targetSuffix = targetEntryText.substring(i + patternText.length)
                return K2ExtractableSubstringInfo(targetEntry, targetEntry, targetPrefix, targetSuffix, patternInfo.isString)
            }

            val matchStartByText = patternInfo.startEntry is KtLiteralStringTemplateEntry
            val matchEndByText = patternInfo.endEntry is KtLiteralStringTemplateEntry

            val targetPrefix = if (matchStartByText) {
                if (targetEntry !is KtLiteralStringTemplateEntry) continue

                val patternText = patternInfo.startEntry.text.substring(prefixLength)
                if (!targetEntryText.endsWith(patternText)) continue
                targetEntryText.substring(0, targetEntryText.length - patternText.length)
            } else ""

            val lastTargetEntry = targetEntries[index + patternEntries.lastIndex]

            val targetSuffix = if (matchEndByText) {
                if (lastTargetEntry !is KtLiteralStringTemplateEntry) continue

                val patternText = with(patternInfo.endEntry.text) { substring(0, length - suffixLength) }
                val lastTargetEntryText = lastTargetEntry.text
                if (!lastTargetEntryText.startsWith(patternText)) continue
                lastTargetEntryText.substring(patternText.length)
            } else ""

            val fromIndex = if (matchStartByText) 1 else 0
            val toIndex = if (matchEndByText) patternEntries.lastIndex - 1 else patternEntries.lastIndex
            val status = (fromIndex..toIndex).fold(true) { status, patternEntryIndex ->
                val targetEntryToUnify = targetEntries[index + patternEntryIndex]
                val patternEntryToUnify = patternEntries[patternEntryIndex]
                status && targetEntryToUnify.isSemanticMatch(patternEntryToUnify, matchingContext)
            }
            if (!status) continue
            return K2ExtractableSubstringInfo(targetEntry, lastTargetEntry, targetPrefix, targetSuffix, patternInfo.isString)
        }

        return null
    }


    /**
     * @property parameterSubstitution The map that stores the association between pattern parameters and target arguments.
     * Initially, it's filled with parameters and during match, all parameters should receive corresponding argument.
     * All arguments for a parameter should be equal.
     */
    private data class MatchingContext(
        val symbols: MutableMap<KaSymbol, KaSymbol> = mutableMapOf(),
        val blockBodyOwners: MutableMap<KaFunctionSymbol, KaFunctionSymbol> = mutableMapOf(),
        val parameterSubstitution: MutableMap<PsiNamedElement, KtElement?> = mutableMapOf(),
    ) {
        context(KaSession)
        fun areSymbolsEqualOrAssociated(targetSymbol: KaSymbol?, patternSymbol: KaSymbol?): Boolean {
            if (targetSymbol == null || patternSymbol == null) return targetSymbol == null && patternSymbol == null

            if (patternSymbol is KaNamedSymbol) {
                val patternElement = patternSymbol.psi as? PsiNamedElement
                if (patternElement != null && parameterSubstitution.containsKey(patternElement)) {
                    if (patternSymbol is KaCallableSymbol && targetSymbol is KaCallableSymbol) {
                        if (!targetSymbol.returnType.isSubtypeOf(patternSymbol.returnType)) return false
                    }
                    val expression =
                        KtPsiFactory(patternElement.project).createExpression((targetSymbol as KaNamedSymbol).name.asString())
                    val oldElement = parameterSubstitution.put(patternElement, expression)
                    return oldElement !is KtElement || oldElement.text == expression.text
                }
            }

            return targetSymbol == patternSymbol || symbols[targetSymbol] == patternSymbol
        }

        context(KaSession)
        fun areBlockBodyOwnersEqualOrAssociated(targetFunction: KaFunctionSymbol, patternFunction: KaFunctionSymbol): Boolean =
            targetFunction == patternFunction || blockBodyOwners[targetFunction] == patternFunction

        // TODO: current approach doesn't work on pairs of types such as `List<U>` and `List<T>`, where `U` and `T` are associated
        context(KaSession)
        fun areTypesEqualOrAssociated(targetType: KaType?, patternType: KaType?): Boolean {
            if (targetType == null || patternType == null) return targetType == null && patternType == null

            return targetType.semanticallyEquals(patternType) ||
                    targetType is KaTypeParameterType &&
                    patternType is KaTypeParameterType &&
                    symbols[targetType.symbol] == patternType.symbol
        }

        context(KaSession)
        fun associateSymbolsForDeclarations(targetDeclaration: KtDeclaration, patternDeclaration: KtDeclaration) {
            val targetSymbol = targetDeclaration.symbol
            val patternSymbol = patternDeclaration.symbol

            if (targetSymbol is KaDestructuringDeclarationSymbol && patternSymbol is KaDestructuringDeclarationSymbol) {
                for ((targetEntry, patternEntry) in targetSymbol.entries.zip(patternSymbol.entries)) {
                    symbols[targetEntry] = patternEntry
                }
            } else {
                symbols[targetSymbol] = patternSymbol
            }
        }

        context(KaSession)
        fun associateSymbolsForBlockBodyOwners(targetFunction: KtFunction, patternFunction: KtFunction) {
            check(targetFunction.bodyExpression is KtBlockExpression && patternFunction.bodyExpression is KtBlockExpression)

            blockBodyOwners[targetFunction.getFunctionLikeSymbol()] = patternFunction.getFunctionLikeSymbol()
        }

        context(KaSession)
        fun associateSingleParameterSymbolsForAnonymousFunctions(
            targetFunction: KtFunction,
            patternFunction: KtFunction,
        ) {
            val targetSymbol = getSingleParameterSymbolForAnonymousFunctionOrNull(targetFunction) ?: return
            val patternSymbol = getSingleParameterSymbolForAnonymousFunctionOrNull(patternFunction) ?: return

            symbols[targetSymbol] = patternSymbol
        }

        context(KaSession)
        fun associateReceiverParameterSymbolsForCallables(
            targetDeclaration: KtCallableDeclaration,
            patternDeclaration: KtCallableDeclaration,
        ) {
            val targetSymbol = targetDeclaration.getCallableSymbol().receiverParameter ?: return
            val patternSymbol = patternDeclaration.getCallableSymbol().receiverParameter ?: return

            symbols[targetSymbol] = patternSymbol
        }

        context(KaSession)
        private fun getSingleParameterSymbolForAnonymousFunctionOrNull(function: KtFunction): KaValueParameterSymbol? {
            val anonymousFunction = function.symbol as? KaAnonymousFunctionSymbol ?: return null
            return anonymousFunction.valueParameters.singleOrNull()
        }
    }

    context(KaSession)
    private fun elementsMatchOrBothAreNull(targetElement: KtElement?, patternElement: KtElement?, context: MatchingContext): Boolean {
        if (targetElement == null || patternElement == null) return targetElement == null && patternElement == null
        if (patternElement is KtSimpleNameExpression) {
            val param = patternElement.mainReference.resolveToSymbol()?.psi as? PsiNamedElement
            if (param != null && context.parameterSubstitution.containsKey(param)) {
                val oldElement = context.parameterSubstitution.put(param, targetElement)
                return oldElement !is KtElement || oldElement.isSemanticMatch(targetElement)
            }
        }
        return targetElement.isSemanticMatch(patternElement, context)
    }

    private class VisitingMatcher(
        private val analysisSession: KaSession,
        private val context: MatchingContext,
    ) : KtVisitor<Boolean, KtElement>() {
        private fun elementsMatchOrBothAreNull(targetElement: KtElement?, patternElement: KtElement?): Boolean {
            if (targetElement == null || patternElement == null) return targetElement == null && patternElement == null

            return targetElement.accept(visitor = this, patternElement)
        }

        override fun visitKtElement(element: KtElement, data: KtElement): Boolean = false

        override fun visitDeclaration(dcl: KtDeclaration, data: KtElement): Boolean {
            val patternDcl = data as? KtDeclaration ?: return false

            if (dcl is KtValVarKeywordOwner && patternDcl is KtValVarKeywordOwner) {
                if (dcl.valOrVarKeyword?.elementType != patternDcl.valOrVarKeyword?.elementType) return false
            }

            if (dcl is KtTypeParameterListOwner && patternDcl is KtTypeParameterListOwner) {
                if (!elementsMatchOrBothAreNull(dcl.typeParameterList, patternDcl.typeParameterList)) return false
            }

            if (dcl is KtFunction && patternDcl is KtFunction) {
                if (dcl.isFunctionLiteralWithoutParameterSpecification() || patternDcl.isFunctionLiteralWithoutParameterSpecification()) {
                    with(analysisSession) {
                        if (!areFunctionsWithZeroOrOneParametersMatchingByResolve(dcl, patternDcl, context)) return false
                        context.associateSingleParameterSymbolsForAnonymousFunctions(dcl, patternDcl)
                    }
                } else {
                    if (!elementsMatchOrBothAreNull(dcl.valueParameterList, patternDcl.valueParameterList)) return false
                }
            }

            if (dcl is KtCallableDeclaration && patternDcl is KtCallableDeclaration) {
                with(analysisSession) {
                    if (!areReceiverParametersMatchingByResolve(dcl, patternDcl, context)) return false
                    if (!areReturnTypesOfDeclarationsMatchingByResolve(dcl, patternDcl, context)) return false
                    context.associateReceiverParameterSymbolsForCallables(dcl, patternDcl)
                }
            }

            with(analysisSession) {
                context.associateSymbolsForDeclarations(dcl, patternDcl)
            }

            if (dcl is KtDeclarationWithBody && patternDcl is KtDeclarationWithBody) {
                if (!elementsMatchOrBothAreNull(dcl.bodyExpression, patternDcl.bodyExpression)) return false
            }

            if (dcl is KtDeclarationWithInitializer && patternDcl is KtDeclarationWithInitializer) {
                if (!elementsMatchOrBothAreNull(dcl.initializer, patternDcl.initializer)) return false
            }

            return true
        }

        override fun visitNamedFunction(function: KtNamedFunction, data: KtElement): Boolean {
            val patternFunction = data as? KtNamedFunction ?: return false
            return visitDeclaration(function, patternFunction)
        }

        override fun visitProperty(property: KtProperty, data: KtElement?): Boolean {
            val patternProperty = data as? KtProperty ?: return false
            return visitDeclaration(property, patternProperty)
        }

        override fun visitDestructuringDeclaration(multiDeclaration: KtDestructuringDeclaration, data: KtElement): Boolean {
            val patternMultiDeclaration = data as? KtDestructuringDeclaration ?: return false
            if (multiDeclaration.entries.size != patternMultiDeclaration.entries.size) return false
            return visitDeclaration(multiDeclaration, patternMultiDeclaration)
        }

        override fun visitTypeParameterList(list: KtTypeParameterList, data: KtElement): Boolean {
            val patternList = data as? KtTypeParameterList ?: return false

            if (list.parameters.size != patternList.parameters.size) return false
            for ((targetParameter, patternParameter) in list.parameters.zip(patternList.parameters)) {
                if (!elementsMatchOrBothAreNull(targetParameter, patternParameter)) return false
            }

            return true
        }

        override fun visitTypeParameter(parameter: KtTypeParameter, data: KtElement): Boolean {
            val patternParameter = data as? KtTypeParameter ?: return false
            with(analysisSession) {
                if (!areTypeParametersMatchingByResolve(parameter, patternParameter, context)) return false
            }
            return visitDeclaration(parameter, patternParameter)
        }

        override fun visitParameterList(list: KtParameterList, data: KtElement): Boolean {
            val patternList = data as? KtParameterList ?: return false

            if (list.parameters.size != patternList.parameters.size) return false
            for ((targetParameter, patternParameter) in list.parameters.zip(patternList.parameters)) {
                if (!elementsMatchOrBothAreNull(targetParameter, patternParameter)) return false
            }

            return true
        }

        override fun visitParameter(parameter: KtParameter, data: KtElement): Boolean {
            val patternParameter = data as? KtParameter ?: return false
            return visitDeclaration(parameter, patternParameter)
        }

        override fun visitScript(script: KtScript, data: KtElement): Boolean = false // TODO()

        override fun visitTypeReference(typeReference: KtTypeReference, data: KtElement): Boolean {
            val patternTypeReference = data as? KtTypeReference ?: return false
            with(analysisSession) {
                if (!areTypeReferencesMatchingByResolve(typeReference, patternTypeReference, context)) return false
            }
            return true
        }

        override fun visitExpression(expression: KtExpression, data: KtElement): Boolean {
            val patternExpression = data.deparenthesized() as? KtExpression ?: return false
            if (patternExpression is KtQualifiedExpression) {
                return patternExpression.selectorExpression?.let { visitExpression(expression, it) } == true
            }
            if (expression.isSafeCall() != patternExpression.isSafeCall()) return false
            if (expression.isAssignmentOperation() != patternExpression.isAssignmentOperation()) return false
            if (expression.isInsideKtTypeReference != patternExpression.isInsideKtTypeReference) return false
            if (expression is KtSimpleNameExpression != patternExpression is KtSimpleNameExpression) return false
            if (patternExpression !is KtReferenceExpression && patternExpression !is KtOperationExpression) return false

            with(analysisSession) {
                if (!areCallsMatchingByResolve(expression, patternExpression, context)) return false
            }
            return true
        }

        override fun visitLoopExpression(loopExpression: KtLoopExpression, data: KtElement): Boolean = false // TODO()

        override fun visitConstantExpression(expression: KtConstantExpression, data: KtElement): Boolean {
            val patternExpression = data.deparenthesized() as? KtConstantExpression ?: return false

            return expression.text == patternExpression.text
        }

        override fun visitLabeledExpression(expression: KtLabeledExpression, data: KtElement): Boolean = false // TODO()

        override fun visitUnaryExpression(expression: KtUnaryExpression, data: KtElement): Boolean {
            val patternExpression = data.deparenthesized() as? KtExpression ?: return false
            if (patternExpression is KtUnaryExpression) {
                if (expression::class != patternExpression::class) return false
                if (expression.operationToken != patternExpression.operationToken) return false
                with(analysisSession) {
                    if (!areReferencesMatchingByResolve(expression.mainReference, patternExpression.mainReference, context)) return false
                }
                return elementsMatchOrBothAreNull(expression.baseExpression, patternExpression.baseExpression)
            }
            return visitExpression(expression, patternExpression)
        }

        override fun visitBinaryExpression(expression: KtBinaryExpression, data: KtElement): Boolean {
            val patternExpression = data.deparenthesized() as? KtExpression ?: return false
            if (patternExpression is KtBinaryExpression) {
                if (expression.operationToken != patternExpression.operationToken) return false
                with(analysisSession) {
                    if (!areReferencesMatchingByResolve(expression.mainReference, patternExpression.mainReference, context)) return false
                }
                return elementsMatchOrBothAreNull(expression.left, patternExpression.left) &&
                        elementsMatchOrBothAreNull(expression.right, patternExpression.right)
            }
            return visitExpression(expression, patternExpression)
        }

        override fun visitReturnExpression(expression: KtReturnExpression, data: KtElement): Boolean {
            val patternExpression = data.deparenthesized() as? KtReturnExpression ?: return false
            with(analysisSession) {
                if (!areReturnTargetsMatchingByResolve(expression, patternExpression, context)) return false
            }
            return elementsMatchOrBothAreNull(expression.returnedExpression, patternExpression.returnedExpression)
        }

        override fun visitThrowExpression(expression: KtThrowExpression, data: KtElement): Boolean = false // TODO()

        override fun visitBreakExpression(expression: KtBreakExpression, data: KtElement): Boolean {
            val patternExpression = data.deparenthesized() as? KtBreakExpression ?: return false

            return findRelevantLoopForExpression(expression) == findRelevantLoopForExpression(patternExpression)
        }

        override fun visitContinueExpression(expression: KtContinueExpression, data: KtElement): Boolean {
            val patternExpression = data.deparenthesized() as? KtContinueExpression ?: return false

            return findRelevantLoopForExpression(expression) == findRelevantLoopForExpression(patternExpression)
        }

        override fun visitIfExpression(expression: KtIfExpression, data: KtElement): Boolean {
            val patternExpression = data.deparenthesized() as? KtIfExpression ?: return false

            if (!elementsMatchOrBothAreNull(expression.condition, patternExpression.condition)) return false
            if (!elementsMatchOrBothAreNull(expression.then, patternExpression.then)) return false
            if (!elementsMatchOrBothAreNull(expression.`else`, patternExpression.`else`)) return false

            return true
        }

        override fun visitWhenExpression(expression: KtWhenExpression, data: KtElement): Boolean {
            val patternExpression = data.deparenthesized() as? KtWhenExpression ?: return false

            if (expression.entries.size != patternExpression.entries.size) return false
            if (!elementsMatchOrBothAreNull(expression.subjectExpression, patternExpression.subjectExpression)) return false

            for ((targetEntry, patternEntry) in expression.entries.zip(patternExpression.entries)) {
                if (targetEntry.conditions.size != patternEntry.conditions.size) return false
                for ((targetCondition, patternCondition) in targetEntry.conditions.zip(patternEntry.conditions)) {
                    if (!elementsMatchOrBothAreNull(targetCondition, patternCondition)) return false
                }
                if (!elementsMatchOrBothAreNull(targetEntry.expression, patternEntry.expression)) return false
            }

            return true
        }

        override fun visitCollectionLiteralExpression(expression: KtCollectionLiteralExpression, data: KtElement): Boolean = false // TODO()

        override fun visitTryExpression(expression: KtTryExpression, data: KtElement): Boolean = false

        // TODO: match lambdas with anonymous functions
        override fun visitLambdaExpression(expression: KtLambdaExpression, data: KtElement): Boolean {
            val patternExpression = data as? KtLambdaExpression ?: return false

            return visitDeclaration(expression.functionLiteral, patternExpression.functionLiteral)
        }

        override fun visitAnnotatedExpression(expression: KtAnnotatedExpression, data: KtElement): Boolean = false // TODO()

        override fun visitQualifiedExpression(expression: KtQualifiedExpression, data: KtElement): Boolean {
            val targetSelectorExpression = expression.selectorExpression ?: return false
            return visitExpression(targetSelectorExpression, data)
        }

        override fun visitDoubleColonExpression(expression: KtDoubleColonExpression, data: KtElement): Boolean = false // TODO()

        override fun visitObjectLiteralExpression(expression: KtObjectLiteralExpression, data: KtElement): Boolean = false // TODO()

        // TODO: single-statement-blocks can semantically match non-block-expressions, e.g. in if, when
        override fun visitBlockExpression(expression: KtBlockExpression, data: KtElement): Boolean {
            val patternExpression = data as? KtBlockExpression ?: return false
            if (expression.statements.size != patternExpression.statements.size) return false

            val targetFunction = expression.parent as? KtFunction
            val patternFunction = patternExpression.parent as? KtFunction
            if (targetFunction != null || patternFunction != null) {
                if (targetFunction == null || patternFunction == null) return false
                with(analysisSession) { context.associateSymbolsForBlockBodyOwners(targetFunction, patternFunction) }
            }

            for ((targetStatement, patternStatement) in expression.statements.zip(patternExpression.statements)) {
                if (!elementsMatchOrBothAreNull(targetStatement, patternStatement)) return false
            }
            return true
        }

        override fun visitTypeArgumentList(typeArgumentList: KtTypeArgumentList, data: KtElement): Boolean {
            val patternTypeArgumentList = data as? KtTypeArgumentList ?: return false
            if (typeArgumentList.arguments.size != patternTypeArgumentList.arguments.size) return false
            for ((targetTypeArgument, patternTypeArgument) in typeArgumentList.arguments.zip(patternTypeArgumentList.arguments)) {
                if (!elementsMatchOrBothAreNull(targetTypeArgument?.typeReference, patternTypeArgument?.typeReference)) return false
            }
            return true
        }

        override fun visitThisExpression(expression: KtThisExpression, data: KtElement): Boolean {
            val patternExpression = data.deparenthesized() as? KtThisExpression ?: return false
            with(analysisSession) {
                if (!areReferencesMatchingByResolve(expression.mainReference, patternExpression.mainReference, context)) return false
            }
            return true
        }

        override fun visitSuperExpression(expression: KtSuperExpression, data: KtElement): Boolean {
            val patternExpression = data.deparenthesized() as? KtSuperExpression ?: return false
            with(analysisSession) {
                if (!areReferencesMatchingByResolve(expression.mainReference, patternExpression.mainReference, context)) return false
            }
            return true
        }

        override fun visitParenthesizedExpression(expression: KtParenthesizedExpression, data: KtElement): Boolean {
            val deparenthesized = expression.deparenthesized()
            if (expression == deparenthesized) return false
            return deparenthesized.accept(visitor = this, data)
        }

        override fun visitAnonymousInitializer(initializer: KtAnonymousInitializer, data: KtElement): Boolean = false // TODO()

        override fun visitPropertyAccessor(accessor: KtPropertyAccessor, data: KtElement): Boolean = false // TODO()

        override fun visitBackingField(accessor: KtBackingField, data: KtElement): Boolean = false // TODO()

        override fun visitBinaryWithTypeRHSExpression(expression: KtBinaryExpressionWithTypeRHS, data: KtElement): Boolean {
            val patternExpression = data.deparenthesized() as? KtBinaryExpressionWithTypeRHS ?: return false

            if (expression.operationToken != patternExpression.operationToken) return false
            if (!expression.left.accept(this, patternExpression.left)) return false
            if (!elementsMatchOrBothAreNull(expression.right, patternExpression.right)) return false

            return true
        }

        override fun visitEscapeStringTemplateEntry(
            entry: KtEscapeStringTemplateEntry,
            data: KtElement?
        ): Boolean? {
            val patternEntry = data as? KtEscapeStringTemplateEntry ?: return false
            return entry.unescapedValue == patternEntry.unescapedValue
        }

        override fun visitStringTemplateEntryWithExpression(
            entry: KtStringTemplateEntryWithExpression,
            data: KtElement?
        ): Boolean? {
            val patternEntry = data?.deparenthesized() as? KtStringTemplateEntryWithExpression ?: return false
            return elementsMatchOrBothAreNull(entry.expression, patternEntry.expression)
        }

        override fun visitLiteralStringTemplateEntry(
            entry: KtLiteralStringTemplateEntry,
            data: KtElement?
        ): Boolean? {
            val patternLiteral = data?.deparenthesized() as? KtLiteralStringTemplateEntry ?: return false
            return entry.text == patternLiteral.text
        }

        override fun visitStringTemplateExpression(expression: KtStringTemplateExpression, data: KtElement): Boolean {
            val patternExpression = data.deparenthesized() as? KtStringTemplateExpression ?: return false

            if (expression.entries.size != patternExpression.entries.size) return false
            for ((targetEntry, patternEntry) in expression.entries.zip(patternExpression.entries)) {
                if (!elementsMatchOrBothAreNull(targetEntry, patternEntry)) return false
            }

            return true
        }

        override fun visitNamedDeclaration(declaration: KtNamedDeclaration, data: KtElement): Boolean = false // TODO()

        override fun visitIsExpression(expression: KtIsExpression, data: KtElement): Boolean {
            val patternExpression = data.deparenthesized() as? KtIsExpression ?: return false

            if (expression.operationToken != patternExpression.operationToken) return false
            if (!expression.leftHandSide.accept(this, patternExpression.leftHandSide)) return false
            if (!elementsMatchOrBothAreNull(expression.typeReference, patternExpression.typeReference)) return false

            return true
        }

        override fun visitWhenConditionInRange(condition: KtWhenConditionInRange, data: KtElement): Boolean = false // TODO()

        override fun visitWhenConditionIsPattern(condition: KtWhenConditionIsPattern, data: KtElement): Boolean = false // TODO()

        override fun visitWhenConditionWithExpression(condition: KtWhenConditionWithExpression, data: KtElement): Boolean {
            val patternCondition = data as? KtWhenConditionWithExpression ?: return false
            return elementsMatchOrBothAreNull(condition.expression, patternCondition.expression)
        }
    }

    context(KaSession)
    private fun areCallsMatchingByResolve(
        targetExpression: KtExpression,
        patternExpression: KtExpression,
        context: MatchingContext,
    ): Boolean {
        if (areNonCallsMatchingByResolve(targetExpression, patternExpression, context)) return true

        val targetCallInfo = targetExpression.resolveToCall() ?: return false
        val patternCallInfo = patternExpression.resolveToCall() ?: return false

        if (targetCallInfo is KaErrorCallInfo && patternCallInfo is KaErrorCallInfo) {
            if (targetCallInfo.isUnresolvedCall() != patternCallInfo.isUnresolvedCall()) return false
            if (targetCallInfo.isUnresolvedCall() && patternCallInfo.isUnresolvedCall()) {
                if (!areUnresolvedCallsMatchingByResolve(targetExpression, patternExpression, context)) return false
            }
        }

        val targetCall = targetCallInfo.calls.singleOrNull()
        val patternCall = patternCallInfo.calls.singleOrNull()

        if (targetCall?.javaClass != patternCall?.javaClass) return false

        if (targetCall is KaCallableMemberCall<*, *> && patternCall is KaCallableMemberCall<*, *>) {
            val targetAppliedSymbol = targetCall.partiallyAppliedSymbol
            val patternAppliedSymbol = patternCall.partiallyAppliedSymbol

            if (!context.areSymbolsEqualOrAssociated(targetCall.symbol, patternCall.symbol)) return false

            for ((targetTypeArgument, patternTypeArgument) in targetCall.getTypeArguments().zipWithNulls(patternCall.getTypeArguments())) {
                if (!context.areTypesEqualOrAssociated(targetTypeArgument, patternTypeArgument)) return false
            }

            if (!areReceiversMatching(targetAppliedSymbol.dispatchReceiver, patternAppliedSymbol.dispatchReceiver, context)) return false
            if (!areReceiversMatching(targetAppliedSymbol.extensionReceiver, patternAppliedSymbol.extensionReceiver, context)) return false
        }

        val targetArguments = targetExpression.getArgumentsAndSortThemIfCallIsPresent(targetCall as? KaFunctionCall<*>)
        val patternArguments = patternExpression.getArgumentsAndSortThemIfCallIsPresent(patternCall as? KaFunctionCall<*>)

        for ((targetArgument, patternArgument) in targetArguments.zipWithNulls(patternArguments)) {
            if (!elementsMatchOrBothAreNull(targetArgument, patternArgument, context)) return false
        }

        return true
    }

    context(KaSession)
    private fun areNonCallsMatchingByResolve(
        targetExpression: KtExpression,
        patternExpression: KtExpression,
        context: MatchingContext
    ): Boolean {
        if (targetExpression !is KtNameReferenceExpression || patternExpression !is KtNameReferenceExpression) return false

        val targetSymbol = targetExpression.mainReference.resolveToSymbol().takeUnless { it is KaCallableSymbol } ?: return false
        val patternSymbol = patternExpression.mainReference.resolveToSymbol().takeUnless { it is KaCallableSymbol } ?: return false

        return context.areSymbolsEqualOrAssociated(targetSymbol, patternSymbol)
    }

    context(KaSession)
    private fun KtExpression.getArgumentsAndSortThemIfCallIsPresent(call: KaFunctionCall<*>?): List<KtExpression?> {
        val allArguments = getArgumentOrIndexExpressions(sourceElement = this)

        if (call == null) return allArguments

        val signature = call.partiallyAppliedSymbol.signature
        val mappedArguments = mapArgumentsToParameterIndices(sourceElement = this, signature, call.argumentMapping)
        val sortedMappedArguments = mappedArguments.toList()
            .sortedWith(compareBy({ (_, parameterIndex) -> parameterIndex }, { (argument, _) -> argument.startOffset }))
            .map { (argument, _) -> argument }

        // `mappedArguments` obtained from `argumentMapping` differ from argument expressions from `KtValueArgumentList` in case of
        // parenthesized expressions; TODO: check if it should be fixed from Analysis API side;
        return sortedMappedArguments + allArguments.filterNot { it?.safeDeparenthesize() in mappedArguments }
    }

    context(KaSession)
    private fun areUnresolvedCallsMatchingByResolve(
        targetExpression: KtExpression,
        patternExpression: KtExpression,
        context: MatchingContext,
    ): Boolean {
        if (targetExpression::class != patternExpression::class) return false

        val targetCallee = targetExpression.getCalleeExpressionIfAny()
        val patternCallee = patternExpression.getCalleeExpressionIfAny()

        if (targetCallee?.text != patternCallee?.text) return false
        if (targetCallee?.isCalleeInCall() != patternCallee?.isCalleeInCall()) return false

        if (
            !elementsMatchOrBothAreNull(targetExpression.getTypeArgumentList(), patternExpression.getTypeArgumentList(), context) ||
            !elementsMatchOrBothAreNull(targetExpression.getReceiverForSelector(), patternExpression.getReceiverForSelector(), context)
        ) return false

        return true
    }

    context(KaSession)
    private fun areReceiversMatching(
        targetReceiver: KaReceiverValue?,
        patternReceiver: KaReceiverValue?,
        context: MatchingContext,
    ): Boolean = when (targetReceiver) {
        is KaImplicitReceiverValue -> {
            when (patternReceiver) {
                is KaImplicitReceiverValue -> context.areSymbolsEqualOrAssociated(targetReceiver.symbol, patternReceiver.symbol)
                is KaExplicitReceiverValue -> {
                    val patternSymbol = patternReceiver.getSymbolForThisExpressionOrNull()
                    patternSymbol != null && context.areSymbolsEqualOrAssociated(targetReceiver.symbol, patternSymbol)
                }

                else -> false
            }
        }

        is KaSmartCastedReceiverValue -> false // TODO()

        is KaExplicitReceiverValue -> {
            when (patternReceiver) {
                is KaImplicitReceiverValue -> {
                    val targetSymbol = targetReceiver.getSymbolForThisExpressionOrNull()
                    targetSymbol != null && context.areSymbolsEqualOrAssociated(targetSymbol, patternReceiver.symbol)
                }

                is KaExplicitReceiverValue -> targetReceiver.expression.isSemanticMatch(patternReceiver.expression, context)
                else -> false
            }
        }

        null -> patternReceiver == null
    }

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    private fun KaCallableMemberCall<*, *>.getTypeArguments(): List<KaType?> = symbol.typeParameters.map { typeArgumentsMapping[it] }

    context(KaSession)
    private fun KaExplicitReceiverValue.getSymbolForThisExpressionOrNull(): KaSymbol? =
        (expression as? KtThisExpression)?.mainReference?.resolveToSymbol()

    context(KaSession)
    private fun areReferencesMatchingByResolve(
        targetReference: KtReference,
        patternReference: KtReference,
        context: MatchingContext,
    ): Boolean = context.areSymbolsEqualOrAssociated(targetReference.resolveToSymbol(), patternReference.resolveToSymbol())

    context(KaSession)
    private fun areReturnTargetsMatchingByResolve(
        targetExpression: KtReturnExpression,
        patternExpression: KtReturnExpression,
        context: MatchingContext,
    ): Boolean {
        val targetReturnTargetSymbol = targetExpression.targetSymbol as? KaFunctionSymbol ?: return false
        val patternReturnTargetSymbol = patternExpression.targetSymbol as? KaFunctionSymbol ?: return false

        return context.areBlockBodyOwnersEqualOrAssociated(targetReturnTargetSymbol, patternReturnTargetSymbol)
    }

    context(KaSession)
    private fun areReturnTypesOfDeclarationsMatchingByResolve(
        targetDeclaration: KtCallableDeclaration,
        patternDeclaration: KtCallableDeclaration,
        context: MatchingContext,
    ): Boolean = context.areTypesEqualOrAssociated(targetDeclaration.returnType, patternDeclaration.returnType)

    context(KaSession)
    private fun areReceiverParametersMatchingByResolve(
        targetDeclaration: KtCallableDeclaration,
        patternDeclaration: KtCallableDeclaration,
        context: MatchingContext
    ): Boolean = context.areTypesEqualOrAssociated(
        targetDeclaration.getCallableSymbol().receiverType,
        patternDeclaration.getCallableSymbol().receiverType,
    )

    context(KaSession)
    private fun areFunctionsWithZeroOrOneParametersMatchingByResolve(
        targetFunction: KtFunction,
        patternFunction: KtFunction,
        context: MatchingContext,
    ): Boolean {
        val targetParameters = targetFunction.getFunctionLikeSymbol().valueParameters
        val patternParameters = patternFunction.getFunctionLikeSymbol().valueParameters
        if (targetParameters.size > 1 || patternParameters.size > 1) return false

        return context.areTypesEqualOrAssociated(targetParameters.singleOrNull()?.returnType, patternParameters.singleOrNull()?.returnType)
    }

    context(KaSession)
    private fun areTypeParametersMatchingByResolve(
        targetParameter: KtTypeParameter,
        patternParameter: KtTypeParameter,
        context: MatchingContext,
    ): Boolean {
        val targetSymbol = targetParameter.symbol
        val patternSymbol = patternParameter.symbol

        // TODO: should we check variance and reified modifier?
        if (targetSymbol.upperBounds.size != patternSymbol.upperBounds.size) return false
        for ((targetUpperBound, patternUpperBound) in targetSymbol.upperBounds.zip(patternSymbol.upperBounds)) {
            if (!context.areTypesEqualOrAssociated(targetUpperBound, patternUpperBound)) return false
        }
        return true
    }

    context(KaSession)
    private fun areTypeReferencesMatchingByResolve(
        targetTypeReference: KtTypeReference,
        patternTypeReference: KtTypeReference,
        context: MatchingContext
    ): Boolean = context.areTypesEqualOrAssociated(targetTypeReference.type, patternTypeReference.type)

    context(KaSession)
    private fun KtFunction.getFunctionLikeSymbol(): KaFunctionSymbol = symbol as KaFunctionSymbol

    context(KaSession)
    private fun KtCallableDeclaration.getCallableSymbol(): KaCallableSymbol = symbol as KaCallableSymbol

    private val KtInstanceExpressionWithLabel.mainReference: KtReference get() = instanceReference.mainReference

    private val KtOperationExpression.mainReference: KtSimpleNameReference get() = operationReference.mainReference

    private val KtOperationExpression.operationToken: IElementType get() = operationReference.getReferencedNameElementType()

    private fun KtElement.deparenthesized(): KtElement = when (this) {
        is KtExpression -> this.safeDeparenthesize()
        else -> this
    }

    private fun KtExpression.isSafeCall(): Boolean = (parent as? KtSafeQualifiedExpression)?.selectorExpression == this

    private fun KtExpression.isAssignmentOperation(): Boolean =
        (this as? KtOperationReferenceExpression)?.operationSignTokenType == KtTokens.EQ

    private fun KtCallableDeclaration.isFunctionLiteralWithoutParameterSpecification(): Boolean =
        this is KtFunctionLiteral && !this.hasParameterSpecification()

    private fun KaErrorCallInfo.isUnresolvedCall(): Boolean = diagnostic is KaFirDiagnostic.UnresolvedReference

    private fun KtExpression.isCalleeInCall(): Boolean = this == (parent as? KtCallElement)?.calleeExpression

    private fun KtExpression.getTypeArgumentList(): KtTypeArgumentList? = (this as? KtCallElement)?.typeArgumentList

    private fun KtElement.getReceiverForSelector(): KtExpression? = getQualifiedExpressionForSelector()?.receiverExpression
}