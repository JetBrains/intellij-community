// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.inline.codeInliner

import com.intellij.psi.createSmartPointer
import com.intellij.psi.search.LocalSearchScope
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.calls.KtCallableMemberCall
import org.jetbrains.kotlin.analysis.api.calls.KtImplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.calls.singleCallOrNull
import org.jetbrains.kotlin.analysis.api.calls.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KtAnonymousObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtClassifierSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionSymbol
import org.jetbrains.kotlin.analysis.api.types.KtErrorType
import org.jetbrains.kotlin.analysis.api.types.KtNonErrorClassType
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.analysis.api.types.KtTypeNullability
import org.jetbrains.kotlin.analysis.api.types.KtUsualClassType
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinDeclarationNameValidator
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggestionProvider
import org.jetbrains.kotlin.idea.base.psi.imports.addImport
import org.jetbrains.kotlin.idea.base.searching.usages.ReferencesSearchScopeHelper
import org.jetbrains.kotlin.idea.core.CollectingNameValidator
import org.jetbrains.kotlin.idea.k2.refactoring.util.LambdaToAnonymousFunctionUtil
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.AbstractCodeInliner
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.CodeToInline
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.CommentHolder
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.ExpressionReplacementPerformer
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.InlineDataKeys.NEW_DECLARATION_KEY
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.InlineDataKeys.RECEIVER_VALUE_KEY
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.InlineDataKeys.USER_CODE_KEY
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.InlineDataKeys.WAS_FUNCTION_LITERAL_ARGUMENT_KEY
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.collectDescendantsOfType
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
    codeToInline: CodeToInline
) : AbstractCodeInliner<KtElement, KtParameter, KtType, KtDeclaration>(call, codeToInline) {
    private val mapping: Map<KtExpression, Name>? = analyze(call) {
        call.resolveCall()?.singleFunctionCallOrNull()?.argumentMapping?.mapValues { e -> e.value.name }
    }

    fun doInline(): KtElement? {
        val qualifiedElement = if (call is KtExpression) {
            call.getQualifiedExpressionForSelector()
                ?: call.callableReferenceExpressionForReference()
                ?: call
        } else call
        val assignment = (qualifiedElement as? KtExpression)
            ?.getAssignmentByLHS()
            ?.takeIf { it.operationToken == KtTokens.EQ }
        val originalDeclaration = analyze(call) { call.resolveCall()?.singleCallOrNull<KtCallableMemberCall<*, *>>()?.partiallyAppliedSymbol?.symbol?.psi as? KtDeclaration } ?: return null
        val callableForParameters = (if (assignment != null && originalDeclaration is KtProperty)
            originalDeclaration.setter?.takeIf { inlineSetter && it.hasBody() } ?: originalDeclaration
        else
            originalDeclaration)
        val elementToBeReplaced = assignment.takeIf { callableForParameters is KtPropertyAccessor } ?: qualifiedElement
        val commentSaver = CommentSaver(elementToBeReplaced, saveLineBreaks = true)
        val ktFile = elementToBeReplaced.containingKtFile
        for ((fqName, allUnder, alias) in codeToInline.fqNamesToImport) {
            if (fqName.startsWith(FqName.fromSegments(listOf("kotlin")))) {
                //todo https://youtrack.jetbrains.com/issue/KTIJ-25928
                continue
            }
            ktFile.addImport(fqName, allUnder, alias)
        }

        var receiver = usageExpression?.receiverExpression()
        receiver?.putCopyableUserData(USER_CODE_KEY, Unit)

        var receiverType =
            receiver?.let {
                analyze(it) {
                    val type = it.getKtType()
                    type to (type?.nullability == KtTypeNullability.NULLABLE)
                }
            }

        if (receiver == null) {
            analyze(call) {
                val partiallyAppliedSymbol =
                    call.resolveCall()?.singleCallOrNull<KtCallableMemberCall<*, *>>()?.partiallyAppliedSymbol
                val receiverValue = partiallyAppliedSymbol?.extensionReceiver ?: partiallyAppliedSymbol?.dispatchReceiver
                if (receiverValue is KtImplicitReceiverValue) {
                    val symbol = receiverValue.symbol
                    val thisText = if (symbol is KtClassifierSymbol && symbol !is KtAnonymousObjectSymbol) {
                        "this@" + symbol.name!!.asString()
                    } else {
                        "this"
                    }
                    receiver = psiFactory.createExpression(thisText)
                    val type = receiverValue.type
                    receiverType = type to (type.nullability == KtTypeNullability.NULLABLE)
                }
            }
        }

        receiver?.putCopyableUserData(RECEIVER_VALUE_KEY, Unit)

        receiver?.let { r ->
            for (instanceExpression in codeToInline.collectDescendantsOfType<KtInstanceExpressionWithLabel> {
                // for this@ClassName we have only option to keep it as is (although it's sometimes incorrect but we have no other options)
                it is KtThisExpression && it.getCopyableUserData(CodeToInline.SIDE_RECEIVER_USAGE_KEY) == null && it.labelQualifier == null
            }) {
                codeToInline.replaceExpression(instanceExpression, r)
            }
        }

        val importDeclarations = codeToInline.fqNamesToImport.mapNotNull { importPath ->
            val target =
                psiFactory.createImportDirective(importPath).mainReference?.resolve() as? KtNamedDeclaration ?: return@mapNotNull null
            importPath to target
        }

        if (elementToBeReplaced is KtSafeQualifiedExpression && receiverType?.second == true) {
            wrapCodeForSafeCall(receiver!!, receiverType?.first, elementToBeReplaced)
        } else if (call is KtBinaryExpression && call.operationToken == KtTokens.IDENTIFIER) {
            keepInfixFormIfPossible(importDeclarations.map { it.second })
        }

        // if the value to be inlined is not used and has no side effects we may drop it
        if (codeToInline.mainExpression != null
            && !codeToInline.alwaysKeepMainExpression
            && assignment == null
            && elementToBeReplaced is KtExpression
            && !analyze(elementToBeReplaced) { elementToBeReplaced.isUsedAsExpression() }
            && !codeToInline.mainExpression.shouldKeepValue(usageCount = 0)
            && elementToBeReplaced.getStrictParentOfType<KtAnnotationEntry>() == null
        ) {
            codeToInline.mainExpression?.getCopyableUserData(CommentHolder.COMMENTS_TO_RESTORE_KEY)?.let { commentHolder ->
                codeToInline.addExtraComments(CommentHolder(emptyList(), commentHolder.leadingComments + commentHolder.trailingComments))
            }

            codeToInline.mainExpression = null
        }

        val lexicalScopeElement = call.parentsWithSelf
            .takeWhile { it !is KtBlockExpression && it !is KtFunction && it !is KtClass && !(it is KtCallableDeclaration && it.parent is KtFile) }
            .last() as KtElement
        val names = mutableSetOf<String>()
        lexicalScopeElement.parent.collectDescendantsOfType<KtProperty>().forEach {
            names.addIfNotNull(it.name)
        }

        val introduceValueForParameters = processValueParameterUsages(callableForParameters)
        introduceVariablesForParameters(elementToBeReplaced, receiver, receiverType?.first, introduceValueForParameters)
        processTypeParameterUsages(
            callElement = call as? KtCallElement,
            typeParameters = (originalDeclaration as? KtCallableDeclaration)?.typeParameters ?: emptyList(),
            namer = { it.nameAsSafeName },
            typeRetriever = {
                analyze(callableForParameters) {
                    call.resolveCall()?.singleFunctionCallOrNull()?.typeArgumentsMapping?.get(it.getSymbol())
                }
            },
            renderType = {
                analyze(call) {
                    it.render(position = Variance.INVARIANT)
                }
            },
            isArrayType = {
                analyze(call) {
                    it.getArrayElementType() != null
                }
            },
            renderClassifier = {
                analyze(call) {
                    (it as? KtNonErrorClassType)?.classId?.asSingleFqName()?.asString()
                }
            }
        )

        codeToInline.extraComments?.restoreComments(elementToBeReplaced)
        findAndMarkNewDeclarations()
        return ExpressionReplacementPerformer(codeToInline, elementToBeReplaced as KtExpression).doIt { range ->
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
            if ((function.getSymbol() as? KtFunctionSymbol)?.isInfix != true) return
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
            for (reference in ReferencesSearchScopeHelper.search(declaration, LocalSearchScope(declaration.parent))) {
                if (reference.element.startOffset < endOfScope) {
                    reference.handleElementRename(newName)
                }
            }

            declaration.nameIdentifier?.replace(psiFactory.createNameIdentifier(newName))
        }
    }

    context(KtAnalysisSession)
    private fun arrayOfFunctionName(elementType: KtType): String {
        return when {
            elementType.isInt -> "kotlin.intArrayOf"
            elementType.isLong -> "kotlin.longArrayOf"
            elementType.isShort -> "kotlin.shortArrayOf"
            elementType.isChar -> "kotlin.charArrayOf"
            elementType.isBoolean -> "kotlin.booleanArrayOf"
            elementType.isByte -> "kotlin.byteArrayOf"
            elementType.isDouble -> "kotlin.doubleArrayOf"
            elementType.isFloat -> "kotlin.floatArrayOf"
            elementType is KtErrorType -> "kotlin.arrayOf"
            else -> "kotlin.arrayOf<" + elementType.render(position = Variance.INVARIANT) + ">"
        }
    }

    override fun argumentForParameter(
        parameter: KtParameter,
        callableDescriptor: KtDeclaration
    ): Argument? {
        if (callableDescriptor is KtPropertyAccessor && callableDescriptor.isSetter) {
            val expr = (call as? KtExpression)
                ?.getQualifiedExpressionForSelectorOrThis()
                ?.getAssignmentByLHS()
                ?.right ?: return null
            return Argument(expr, analyze(call) { expr.getKtType() })
        }

        val expressions = mapping?.entries?.filter { (_, value) ->
            value == parameter.name()
        }?.map { it.key } ?: return null

        if (parameter.isVarArg) {
            return analyze(call) {
                val parameterType = parameter.getReturnKtType()
                val elementType = (parameterType as KtUsualClassType).ownTypeArguments.first().type!!
                val expression = psiFactory.buildExpression {
                    appendFixedText(arrayOfFunctionName(elementType))
                    appendFixedText("(")
                    for ((i, argument) in expressions.withIndex()) {
                        if (i > 0) appendFixedText(",")
                        val valueArgument = argument.parent as KtValueArgument
                        if (valueArgument.getSpreadElement() != null) {
                            appendFixedText("*")
                        }
                        val argumentExpression = valueArgument.getArgumentExpression()!!
                        argumentExpression.putCopyableUserData(USER_CODE_KEY, Unit)
                        appendExpression(argumentExpression)
                    }
                    appendFixedText(")")
                }
                Argument(expression, expression.getKtType())
            }
        } else {
            val expression = expressions.firstOrNull() ?: parameter.defaultValue ?: return null
            val parent = expression.parent
            val isNamed = (parent as? KtValueArgument)?.isNamed() == true
            val resultExpression = run {
                if (expression !is KtLambdaExpression) return@run null
                if (parent is LambdaArgument) {
                    expression.putCopyableUserData(WAS_FUNCTION_LITERAL_ARGUMENT_KEY, Unit)
                }

                //if (!parameter.getReturnKtType().isExtensionFunctionType) return@run null
                analyze(call) { LambdaToAnonymousFunctionUtil.prepareFunctionText(expression) }
                    ?.let { functionText -> LambdaToAnonymousFunctionUtil.convertLambdaToFunction(expression, functionText) }
            } ?: expression
            resultExpression.putCopyableUserData(USER_CODE_KEY, Unit)
            return Argument(resultExpression, analyze(call) { resultExpression.getKtType() }, isNamed = isNamed, expressions.isEmpty())
        }
    }

    override fun KtDeclaration.valueParameters(): List<KtParameter> = (this as? KtDeclarationWithBody)?.valueParameters ?: emptyList()

    override fun KtParameter.name(): Name = nameAsSafeName

    override fun introduceValue(
        value: KtExpression,
        valueType: KtType?,
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