// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.inline.codeInliner

import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.createSmartPointer
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.*
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.*
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinDeclarationNameValidator
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggestionProvider
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.psi.imports.addImport
import org.jetbrains.kotlin.idea.base.searching.usages.ReferencesSearchScopeHelper
import org.jetbrains.kotlin.idea.core.CollectingNameValidator
import org.jetbrains.kotlin.idea.k2.refactoring.util.LambdaToAnonymousFunctionUtil
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.*
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.InlineDataKeys.NEW_DECLARATION_KEY
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.InlineDataKeys.RECEIVER_VALUE_KEY
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.InlineDataKeys.USER_CODE_KEY
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.InlineDataKeys.WAS_CONVERTED_TO_FUNCTION_KEY
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.InlineDataKeys.WAS_FUNCTION_LITERAL_ARGUMENT_KEY
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.utils.addIfNotNull

class CodeInliner(
    private val usageExpression: KtSimpleNameExpression?,
    private val call: KtElement,
    private val inlineSetter: Boolean,
    private val replacement: CodeToInline
) : AbstractCodeInliner<KtElement, KtParameter, KaType, KtDeclaration>(call, replacement) {
    private val mapping: Map<KtExpression, Name>? = analyze(call) {
        treeUpToCall().resolveToCall()?.singleFunctionCallOrNull()?.argumentMapping?.mapValues { e -> e.value.name }
    }

    private fun treeUpToCall(): KtElement {
        val userType = call.parent as? KtUserType ?: return call
        val typeReference = userType.parent as? KtTypeReference ?: return call
        val constructorCalleeExpression = typeReference.parent as? KtConstructorCalleeExpression ?: return call
        val gParent = constructorCalleeExpression.parent
        return gParent as? KtSuperTypeCallEntry ?: gParent as? KtAnnotationEntry ?: call
    }

    @OptIn(KaExperimentalApi::class)
    fun doInline(): KtElement? {
        val qualifiedElement = if (call is KtExpression) {
            call.getQualifiedExpressionForSelector()
                ?: call.callableReferenceExpressionForReference()
                ?: treeUpToCall()
        } else call
        val assignment = (qualifiedElement as? KtExpression)
            ?.getAssignmentByLHS()
            ?.takeIf { it.operationToken == KtTokens.EQ }
        val originalDeclaration = analyze(call) {
            //it might resolve in java method which is converted to kotlin by j2k
            //the originalDeclaration in this case should point to the converted non-physical function
            (call.parent as? KtCallableReferenceExpression
                ?: treeUpToCall())
                .resolveToCall()?.singleCallOrNull<KaCallableMemberCall<*, *>>()?.partiallyAppliedSymbol?.symbol?.psi?.navigationElement as? KtDeclaration ?: replacement.originalDeclaration
        } ?: return null
        val callableForParameters = (if (assignment != null && originalDeclaration is KtProperty)
            originalDeclaration.setter?.takeIf { inlineSetter && it.hasBody() } ?: originalDeclaration
        else
            originalDeclaration)
        val elementToBeReplaced = assignment.takeIf { callableForParameters is KtPropertyAccessor } ?: qualifiedElement
        val commentSaver = CommentSaver(elementToBeReplaced, saveLineBreaks = true)

        // if the value to be inlined is not used and has no side effects we may drop it
        if (codeToInline.mainExpression != null
            && !codeToInline.alwaysKeepMainExpression
            && assignment == null
            && elementToBeReplaced is KtExpression
            && !analyze(elementToBeReplaced) { elementToBeReplaced.isUsedAsExpression }
            && !codeToInline.mainExpression.shouldKeepValue(usageCount = 0)
            && elementToBeReplaced.getStrictParentOfType<KtAnnotationEntry>() == null
        ) {
            codeToInline.mainExpression?.getCopyableUserData(CommentHolder.COMMENTS_TO_RESTORE_KEY)?.let { commentHolder ->
                codeToInline.addExtraComments(CommentHolder(emptyList(), commentHolder.leadingComments + commentHolder.trailingComments))
            }

            codeToInline.mainExpression = null
        }

        val ktFile = elementToBeReplaced.containingKtFile
        for ((path, target) in codeToInline.fqNamesToImport) {
            val (fqName, allUnder, alias) = path
            if (fqName.startsWith(FqName.fromSegments(listOf("kotlin")))) {
                //todo https://youtrack.jetbrains.com/issue/KTIJ-25928
                continue
            }

            if (target?.containingFile == ktFile) {
                continue
            }

            ktFile.addImport(fqName, allUnder, alias)
        }

        var receiver = usageExpression?.receiverExpression()
        receiver?.putCopyableUserData(USER_CODE_KEY, Unit)

        var receiverType =
            receiver?.let {
                analyze(it) {
                    val type = it.expressionType
                    type to (type?.nullability == KaTypeNullability.NULLABLE || type is KaFlexibleType && type.upperBound.nullability == KaTypeNullability.NULLABLE)
                }
            }

        if (receiver == null) {
            analyze(call) {
                val partiallyAppliedSymbol =
                    call.resolveToCall()?.singleCallOrNull<KaCallableMemberCall<*, *>>()?.partiallyAppliedSymbol
                val receiverValue = partiallyAppliedSymbol?.extensionReceiver ?: partiallyAppliedSymbol?.dispatchReceiver
                if (receiverValue is KaImplicitReceiverValue) {
                    val symbol = receiverValue.symbol
                    val thisText = when {
                        symbol is KaClassSymbol && symbol.classKind.isObject && symbol.name != null -> symbol.name!!.asString()
                        symbol is KaClassifierSymbol && symbol !is KaAnonymousObjectSymbol -> "this@" + symbol.name!!.asString()
                        symbol is KaReceiverParameterSymbol -> {
                            val name = (symbol.psi as? KtFunctionLiteral)?.findLabelAndCall()?.first
                                ?: symbol.owningCallableSymbol.callableId?.callableName
                            name?.asString()?.let { "this@$it" } ?: "this"
                        }
                        else -> "this"
                    }
                    receiver = psiFactory.createExpression(thisText)
                    val type = receiverValue.type
                    receiverType = type to (type.nullability == KaTypeNullability.NULLABLE)
                }
            }
        }

        receiver?.putCopyableUserData(RECEIVER_VALUE_KEY, Unit)

        receiver?.let { r ->
            for (instanceExpression in codeToInline.collectDescendantsOfType<KtInstanceExpressionWithLabel> {
                it is KtThisExpression
            }) {
                if (instanceExpression.getCopyableUserData(CodeToInline.DELETE_RECEIVER_USAGE_KEY) != null) {
                    val parent = instanceExpression.parent
                    if (parent is KtDotQualifiedExpression) {
                        val selectorExpression = parent.selectorExpression
                        if (selectorExpression != null) {
                            codeToInline.replaceExpression(parent, selectorExpression)
                        }
                    } else if (!parent.isPhysical) {
                        codeToInline.replaceExpression(instanceExpression, instanceExpression.instanceReference)
                    }
                } else if (instanceExpression.getCopyableUserData(CodeToInline.SIDE_RECEIVER_USAGE_KEY) == null) {
                    codeToInline.replaceExpression(instanceExpression, r)
                }
            }
        }

        val introduceValueForParameters = processValueParameterUsages(callableForParameters)

        processTypeParameterUsages(
            callElement = call as? KtCallElement,
            typeParameters = (originalDeclaration as? KtConstructor<*>)?.containingClass()?.typeParameters ?: (originalDeclaration as? KtCallableDeclaration)?.typeParameters ?: emptyList(),
            namer = { it.nameAsSafeName },
            typeRetriever = {
                analyze(call) {
                    call.resolveToCall()?.singleFunctionCallOrNull()?.typeArgumentsMapping?.entries?.find { entry -> entry.key.psi?.navigationElement == it }?.value
                }
            },
            renderType = {
                analyze(call) {
                    (it.approximateToSubPublicDenotable(true)?: it).render(position = Variance.INVARIANT)
                }
            },
            isArrayType = {
                analyze(call) {
                    it.arrayElementType != null
                }
            },
            renderClassifier = {
                analyze(call) {
                    (it as? KaClassType)?.classId?.asSingleFqName()?.asString()
                }
            }
        )

        val lexicalScopeElement = call.parentsWithSelf
            .takeWhile { it !is KtBlockExpression && it !is KtFunction && it !is KtClass && !(it is KtCallableDeclaration && it.parent is KtFile) }
            .last() as KtElement
        val names = mutableSetOf<String>()
        lexicalScopeElement.parent.collectDescendantsOfType<KtProperty>().forEach {
            names.addIfNotNull(it.name)
        }

        val importDeclarations = codeToInline.fqNamesToImport.mapNotNull { importPath ->
            val target =
                psiFactory.createImportDirective(importPath.importPath).mainReference?.resolve() as? KtNamedDeclaration ?: return@mapNotNull null
            importPath to target
        }

        if (elementToBeReplaced is KtSafeQualifiedExpression && receiverType?.second == true) {
            wrapCodeForSafeCall(receiver!!, receiverType.first, elementToBeReplaced)
        } else if (call is KtBinaryExpression && call.operationToken == KtTokens.IDENTIFIER) {
            keepInfixFormIfPossible(importDeclarations.map { it.second })
        }

        codeToInline.convertToCallableReferenceIfNeeded(elementToBeReplaced)
        introduceVariablesForParameters(elementToBeReplaced, receiver, receiverType?.first, introduceValueForParameters)

        codeToInline.extraComments?.restoreComments(elementToBeReplaced)
        findAndMarkNewDeclarations()
        val performer = when (elementToBeReplaced) {
            is KtExpression -> ExpressionReplacementPerformer(codeToInline, elementToBeReplaced)
            is KtAnnotationEntry -> AnnotationEntryReplacementPerformer(codeToInline, elementToBeReplaced)
            is KtSuperTypeCallEntry -> SuperTypeCallEntryReplacementPerformer(codeToInline, elementToBeReplaced)
            else -> error("Unsupported element: $elementToBeReplaced")
        }
        return performer.doIt { range ->
            val pointers = range.filterIsInstance<KtElement>().map { it.createSmartPointer() }.toList()
            val declarations = pointers.mapNotNull { pointer -> pointer.element?.takeIf { it.getCopyableUserData(NEW_DECLARATION_KEY) != null } as? KtNamedDeclaration }
            if (declarations.isNotEmpty()) {
                val endOfScope = pointers.last().element?.endOffset ?: error("Can't find the end of the scope")
                renameDuplicates(declarations, names, endOfScope)
            }
            InlinePostProcessor.postProcessInsertedCode(pointers, commentSaver)
        }
    }

    private fun keepInfixFormIfPossible(importDescriptors: List<KtNamedDeclaration>) {
        if (codeToInline.statementsBefore.isNotEmpty()) return
        val dotQualified = codeToInline.mainExpression as? KtDotQualifiedExpression ?: return
        val receiver = dotQualified.receiverExpression
        if (receiver.getCopyableUserData(RECEIVER_VALUE_KEY) == null) return
        val call = dotQualified.selectorExpression as? KtCallExpression ?: return
        val nameExpression = call.calleeExpression as? KtSimpleNameExpression ?: return
        val function =
            importDescriptors.firstOrNull { it.fqName?.shortName()?.asString() == nameExpression.text } as? KtNamedFunction ?: return

        analyze(function) {
            if ((function.symbol as? KaNamedFunctionSymbol)?.isInfix != true) return
        }
        val argument = call.valueArguments.singleOrNull() ?: return
        if (argument.isNamed()) return
        val argumentExpression = argument.getArgumentExpression() ?: return
        codeToInline.mainExpression = psiFactory.createExpressionByPattern("$0 ${nameExpression.text} $1", receiver, argumentExpression)
    }


    private fun renameDuplicates(
        declarations: List<KtNamedDeclaration>,
        names: MutableSet<String>,
        endOfScope: Int,
    ) {
        val context = declarations.first()
        val declaration2Name = mutableMapOf<KtNamedDeclaration, String>()
        analyze(context) {
            val nameValidator = KotlinDeclarationNameValidator(
                context,
                true,
                KotlinNameSuggestionProvider.ValidatorTarget.VARIABLE
            )
            val validator = CollectingNameValidator { nameValidator.validate(it) }
            for (declaration in declarations) {
                val oldName = declaration.name
                if (oldName != null && !names.add(oldName)) {
                    declaration2Name[declaration] = KotlinNameSuggester.suggestNameByName(oldName, validator)
                }
            }
        }
        declaration2Name.forEach { declaration, newName ->
            for (reference in ReferencesSearchScopeHelper.search(declaration, LocalSearchScope(declaration.parent)).asIterable()) {
                if (reference.element.startOffset < endOfScope) {
                    reference.handleElementRename(newName)
                }
            }

            declaration.nameIdentifier?.replace(psiFactory.createNameIdentifier(newName))
        }
    }

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    private fun arrayOfFunctionName(elementType: KaType): String {
        return when {
            elementType.isIntType -> "kotlin.intArrayOf"
            elementType.isLongType -> "kotlin.longArrayOf"
            elementType.isShortType -> "kotlin.shortArrayOf"
            elementType.isCharType -> "kotlin.charArrayOf"
            elementType.isBooleanType -> "kotlin.booleanArrayOf"
            elementType.isByteType -> "kotlin.byteArrayOf"
            elementType.isDoubleType -> "kotlin.doubleArrayOf"
            elementType.isFloatType -> "kotlin.floatArrayOf"
            elementType is KaErrorType -> "kotlin.arrayOf"
            else -> "kotlin.arrayOf<" + elementType.render(position = Variance.INVARIANT) + ">"
        }
    }

    override fun argumentForParameter(
        parameter: KtParameter,
        callableDescriptor: KtDeclaration
    ): Argument? {
        if (callableDescriptor is KtPropertyAccessor && callableDescriptor.isSetter) {
            return argumentForPropertySetter()
        }

        val argumentExpressionsForParameter = mapping?.entries?.filter { (_, value) ->
            value == parameter.name()
        }?.map { it.key } ?: return null

        if (parameter.isVarArg) {
            return argumentForVarargParameter(argumentExpressionsForParameter, parameter)
        } else {
            return argumentForRegularParameter(argumentExpressionsForParameter, parameter, callableDescriptor)
        }
    }

    private fun argumentForRegularParameter(
        argumentExpressionsForParameter: List<KtExpression>, parameter: KtParameter, callableDeclaration: KtDeclaration
    ): Argument? {
        val expression = argumentExpressionsForParameter.firstOrNull() ?: parameter.defaultValue ?: return null
        val parent = expression.parent
        val isNamed = (parent as? KtValueArgument)?.isNamed() == true
        var resultExpression = run {
            if (expression !is KtLambdaExpression) return@run null
            if (parent is LambdaArgument) {
                expression.putCopyableUserData(WAS_FUNCTION_LITERAL_ARGUMENT_KEY, Unit)
            }

            markNonLocalJumps(expression, parameter)

            analyze(call) {
                val functionType = expression.expressionType as? KaFunctionType
                if ((functionType)?.hasReceiver == true && !functionType.isSuspend) {
                    //expand to function only for types with an extension
                    LambdaToAnonymousFunctionUtil.prepareFunctionText(expression)
                } else {
                    null
                }
            }?.let { functionText ->
                val function = LambdaToAnonymousFunctionUtil.convertLambdaToFunction(expression, functionText)
                function?.putCopyableUserData(WAS_CONVERTED_TO_FUNCTION_KEY, Unit)
                function
            }
        } ?: expression

        markAsUserCode(resultExpression)

        val expressionType = analyze(call) { resultExpression.expressionType }
        if (argumentExpressionsForParameter.isEmpty() && callableDeclaration is KtFunction) {
            //encode default value
            val allParameters = callableDeclaration.valueParameters()
            expression.forEachDescendantOfType<KtSimpleNameExpression> {
                val target = it.mainReference.resolve()
                if (target is KtParameter && target in allParameters) {
                    it.putCopyableUserData(CodeToInline.PARAMETER_USAGE_KEY, target.nameAsSafeName)
                }
            }

            resultExpression = expandTypeArgumentsInParameterDefault(expression) ?: resultExpression
        }

        return Argument(resultExpression, expressionType, isNamed = isNamed, argumentExpressionsForParameter.isEmpty())
    }

    private fun argumentForPropertySetter(): Argument? {
        val expr = (call as? KtExpression)
            ?.getQualifiedExpressionForSelectorOrThis()
            ?.getAssignmentByLHS()
            ?.right ?: return null
        return Argument(expr, analyze(call) { expr.expressionType })
    }

    private fun argumentForVarargParameter(argumentExpressionsForParameter: List<KtExpression>, parameter: KtParameter): Argument? {
        return analyze(call) {
            val single = argumentExpressionsForParameter.singleOrNull()?.parent as? KtValueArgument
            if (single?.getSpreadElement() != null) {
                val expression = argumentExpressionsForParameter.first()
                markAsUserCode(expression)
                return Argument(expression, expression.expressionType, isNamed = single.isNamed())
            }
            val parameterType = parameter.returnType
            val elementType = parameterType.arrayElementType ?: return null
            val expression = psiFactory.buildExpression {
                appendFixedText(arrayOfFunctionName(elementType))
                appendFixedText("(")
                for ((i, argument) in argumentExpressionsForParameter.withIndex()) {
                    if (i > 0) appendFixedText(",")
                    val valueArgument = argument.parent as KtValueArgument
                    if (valueArgument.getSpreadElement() != null) {
                        appendFixedText("*")
                    }
                    val argumentExpression = valueArgument.getArgumentExpression()!!
                    markAsUserCode(argumentExpression)
                    appendExpression(argumentExpression)
                }
                appendFixedText(")")
            }
            Argument(expression, expression.expressionType)
        }
    }

    private fun markAsUserCode(expression: KtExpression) {
        // if type arguments were inserted at the preprocessing stage, markers are already set
        if (expression.children.all { it.getCopyableUserData(USER_CODE_KEY) == null }) {
            expression.putCopyableUserData(USER_CODE_KEY, Unit)
        }
    }

    private fun markNonLocalJumps(lambdaArgumentExpression: KtLambdaExpression, parameter: KtParameter) {
        val ownerDeclaration = parameter.ownerDeclaration
        if (ownerDeclaration !is KtNamedFunction || !ownerDeclaration.hasModifier(KtTokens.INLINE_KEYWORD)) return
        val isJumpPossible = lambdaArgumentExpression.languageVersionSettings.supportsFeature(LanguageFeature.BreakContinueInInlineLambdas)
        if (!isJumpPossible) return
        lambdaArgumentExpression.accept(NonLocalJumpVisitor(lambdaArgumentExpression))
    }

    override fun KtDeclaration.valueParameters(): List<KtParameter> = (this as? KtDeclarationWithBody)?.valueParameters ?: emptyList()

    override fun KtParameter.name(): Name = nameAsSafeName

    override fun introduceValue(
        value: KtExpression,
        valueType: KaType?,
        usages: Collection<KtExpression>,
        expressionToBeReplaced: KtExpression,
        nameSuggestion: String?,
        safeCall: Boolean
    ) {
        analyze(value) {
            codeToInline.introduceValue(value, valueType, usages, expressionToBeReplaced, nameSuggestion, safeCall)
        }
    }
}

private class NonLocalJumpVisitor(val lambdaArgumentExpression: KtLambdaExpression) : KtTreeVisitorVoid() {
    override fun visitBreakExpression(expression: KtBreakExpression) {
        markIfNonLocal(expression)
    }

    override fun visitContinueExpression(expression: KtContinueExpression) {
        markIfNonLocal(expression)
    }

    private fun markIfNonLocal(expression: KtExpressionWithLabel) {
        if (expression.getTargetLabel() != null) return
        val loopForJump = expression.getStrictParentOfType<KtLoopExpression>() ?: return
        if (PsiTreeUtil.isAncestor(loopForJump, lambdaArgumentExpression, true)) {
            val loopToken = loopForJump.getCopyableUserData(InlineDataKeys.NON_LOCAL_JUMP_KEY) ?: NonLocalJumpToken()
            loopForJump.putCopyableUserData(InlineDataKeys.NON_LOCAL_JUMP_KEY, loopToken)
            expression.putCopyableUserData(InlineDataKeys.NON_LOCAL_JUMP_KEY, loopToken)
        }
    }
}
