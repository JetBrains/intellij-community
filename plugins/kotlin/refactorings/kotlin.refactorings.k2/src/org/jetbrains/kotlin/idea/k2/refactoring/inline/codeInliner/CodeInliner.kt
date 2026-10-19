// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.inline.codeInliner

import com.intellij.psi.createSmartPointer
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.util.isAncestor
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.expressions.expressionType
import org.jetbrains.kotlin.analysis.api.expressions.isUsedAsExpression
import org.jetbrains.kotlin.analysis.api.renderer.render
import org.jetbrains.kotlin.analysis.api.resolution.KaImplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.function
import org.jetbrains.kotlin.analysis.api.resolution.simple
import org.jetbrains.kotlin.analysis.api.resolution.single
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.resolution.tryResolveCall
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaAnonymousObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassifierSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaReceiverParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.symbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaFlexibleType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.approximateToDenotableSubtypeOrSelf
import org.jetbrains.kotlin.analysis.api.types.arrayElementType
import org.jetbrains.kotlin.analysis.api.types.isMarkedNullable
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinDeclarationNameValidator
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggestionProvider
import org.jetbrains.kotlin.idea.base.psi.AddLabelUtil
import org.jetbrains.kotlin.idea.base.psi.imports.addImport
import org.jetbrains.kotlin.idea.base.searching.usages.ReferencesSearchScopeHelper
import org.jetbrains.kotlin.idea.codeinsight.utils.callExpression
import org.jetbrains.kotlin.idea.core.CollectingNameValidator
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.AnnotationEntryReplacementPerformer
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.CodeToInline
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.CommentHolder
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.ExpressionReplacementPerformer
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.InlineDataKeys
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.InlineDataKeys.NEW_DECLARATION_KEY
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.InlineDataKeys.PARAMETER_VALUE_KEY
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.InlineDataKeys.RECEIVER_VALUE_KEY
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.InlineDataKeys.USER_CODE_KEY
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.MutableCodeToInline
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.SuperTypeCallEntryReplacementPerformer
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.collectDescendantsOfType
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.toMutable
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtBlockCodeFragment
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtCallableReferenceExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassLiteralExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtConstructorCalleeExpression
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtExpressionCodeFragment
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtInstanceExpressionWithLabel
import org.jetbrains.kotlin.psi.KtIntersectionType
import org.jetbrains.kotlin.psi.KtLabelReferenceExpression
import org.jetbrains.kotlin.psi.KtLabeledExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtSafeQualifiedExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtSuperExpression
import org.jetbrains.kotlin.psi.KtSuperTypeCallEntry
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.buildExpression
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.findLabelAndCall
import org.jetbrains.kotlin.psi.psiUtil.getAssignmentByLHS
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector
import org.jetbrains.kotlin.psi.psiUtil.getReceiverExpression
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.psi.psiUtil.quoteIfNeeded
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.resolution.KtResolvableCall
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.utils.addIfNotNull

class CodeInliner(
    private val usageExpression: KtSimpleNameExpression?,
    private val call: KtElement,
    private val inlineSetter: Boolean,
    private val replacement: CodeToInline
) {
    private val codeToInline = replacement.toMutable()
    private val project = call.project
    private val psiFactory = KtPsiFactory(project)
    private val valueParameterInliner = ValueParameterInliner(call, codeToInline, replacement.originalDeclaration)

    private fun MutableCodeToInline.convertToCallableReferenceIfNeeded(elementToBeReplaced: KtElement) {
        if (elementToBeReplaced !is KtCallableReferenceExpression) return
        val qualified = (mainExpression as? KtQualifiedExpression) ?: return
        val reference = qualified.callExpression?.calleeExpression ?: qualified.selectorExpression ?: return
        val callableReference = if (elementToBeReplaced.receiverExpression == null) {
            psiFactory.createExpressionByPattern("::$0", reference)
        } else {
            psiFactory.createExpressionByPattern("$0::$1", qualified.receiverExpression, reference)
        }
        codeToInline.replaceExpression(qualified, callableReference)
    }

    private fun introduceValueInner(
        value: KtExpression,
        isValueNullable: Boolean?,
        usages: Collection<KtExpression>,
        expressionToBeReplaced: KtExpression,
        nameSuggestion: String? = null,
        safeCall: Boolean = false
    ) {
        analyze(value) {
            codeToInline.introduceValue(value, isValueNullable, usages, expressionToBeReplaced, nameSuggestion, safeCall)
        }
    }

    private fun introduceVariablesForParameters(
        elementToBeReplaced: KtElement,
        receiver: KtExpression?,
        isReceiverNullable: Boolean?,
        introduceValuesForParameters: Collection<IntroduceValueForParameter>
    ) {
        if (elementToBeReplaced is KtExpression) {
            if (receiver != null) {
                val thisReplaced =
                    codeToInline.collectDescendantsOfType<KtExpression> { it.getCopyableUserData(RECEIVER_VALUE_KEY) != null }
                if (receiver.shouldKeepValue(usageCount = thisReplaced.size)) {
                    introduceValueInner(receiver, isReceiverNullable, thisReplaced, elementToBeReplaced)
                }
            }

            for (param in introduceValuesForParameters) {
                val usagesReplaced =
                    codeToInline.collectDescendantsOfType<KtExpression> { it.getCopyableUserData(PARAMETER_VALUE_KEY) == param.parameterName }
                introduceValueInner(
                    param.value,
                    param.isValueNullable,
                    usagesReplaced,
                    elementToBeReplaced,
                    nameSuggestion = param.parameterName.asString()
                )
            }
        }
    }


    context(_: KaSession)
    private fun processTypeParameterUsages(originalDeclaration: KtDeclaration) {
        val typeParameters = (originalDeclaration as? KtConstructor<*>)?.containingClass()?.typeParameters
            ?: (originalDeclaration as? KtCallableDeclaration)?.typeParameters ?: emptyList()

        val callElement = call as? KtCallElement
        val explicitTypeArgs = callElement?.typeArgumentList?.arguments
        if (explicitTypeArgs != null && explicitTypeArgs.size != typeParameters.size) return

        val functionCall = (call as? KtResolvableCall)?.tryResolveCall()?.single?.function

        for ((index, typeParameter) in typeParameters.withIndex()) {
            val parameterName = typeParameter.nameAsSafeName
            val usages = codeToInline.collectDescendantsOfType<KtExpression> {
                it.getCopyableUserData(CodeToInline.TYPE_PARAMETER_USAGE_KEY) == parameterName
            }

            val type = functionCall?.typeArgumentsMapping?.entries?.find { entry ->
                entry.key.psi?.navigationElement == typeParameter
            }?.value ?: continue

            val typeElement = if (explicitTypeArgs != null) { // we use explicit type arguments if available to avoid shortening
                val explicitArgTypeElement = explicitTypeArgs[index].typeReference?.typeElement ?: continue
                explicitArgTypeElement.putCopyableUserData(USER_CODE_KEY, Unit)
                explicitArgTypeElement
            } else {
                psiFactory.createType(type.approximateToDenotableSubtypeOrSelf().render(position = Variance.INVARIANT)).typeElement
                    ?: continue
            }

            val typeClassifier = (type as? KaClassType)?.classId?.asSingleFqName()?.asString()

            for (usage in usages) {
                val parent = usage.parent
                when (parent) {
                    is KtClassLiteralExpression if typeClassifier != null -> {
                        // for class literal ("X::class") we need type arguments only for kotlin.Array
                        val arguments =
                            if (typeElement is KtUserType && type.arrayElementType != null) typeElement.typeArgumentList?.text.orEmpty()
                            else ""
                        codeToInline.replaceExpression(
                            usage, psiFactory.createExpression(typeClassifier + arguments)
                        )
                    }

                    is KtUserType -> {
                        (((parent.parent as? KtTypeReference)?.parent as? KtIntersectionType) ?: parent).replace(typeElement)
                    }

                    else -> {
                        //TODO: tests for this?
                        codeToInline.replaceExpression(usage, psiFactory.createExpression(typeElement.text))
                    }
                }
            }
        }
    }

    fun wrapCodeForSafeCall(receiver: KtExpression, isReceiverNullable: Boolean?, expressionToBeReplaced: KtExpression) {
        if (codeToInline.statementsBefore.isEmpty()) {
            val qualified = codeToInline.mainExpression as? KtQualifiedExpression
            if (qualified != null) {
                if (qualified.receiverExpression.getCopyableUserData(RECEIVER_VALUE_KEY) != null) {
                    if (qualified is KtSafeQualifiedExpression) return // already safe
                    val selector = qualified.selectorExpression
                    if (selector != null) {
                        codeToInline.mainExpression = psiFactory.createExpressionByPattern("$0?.$1", receiver, selector)
                        return
                    }
                }
            }
        }

        if (codeToInline.statementsBefore.isEmpty() || analyze(expressionToBeReplaced) { expressionToBeReplaced.isUsedAsExpression }) {
            val thisReplaced = codeToInline.collectDescendantsOfType<KtExpression> { it.getCopyableUserData(RECEIVER_VALUE_KEY) != null }
            introduceValueInner(receiver, isReceiverNullable, thisReplaced, expressionToBeReplaced, safeCall = true)
        } else {
            codeToInline.mainExpression = psiFactory.buildExpression {
                appendFixedText("if (")
                appendExpression(receiver)
                appendFixedText("!=null)")
                appendFixedText(" {")
                with(codeToInline) {
                    appendExpressionsFromCodeToInline(postfixForMainExpression = "\n")
                }

                appendFixedText("}")
            }

            codeToInline.statementsBefore.clear()
        }
    }

    private fun findAndMarkNewDeclarations() {
        for (it in codeToInline.statementsBefore) {
            if (it is KtNamedDeclaration) {
                it.putCopyableUserData(NEW_DECLARATION_KEY, Unit)
            }
        }
    }

    fun doInline(): KtElement? {
        val qualifiedElement = if (call is KtExpression) {
            call.getQualifiedExpressionForSelector()
                ?: (call.parent as? KtCallableReferenceExpression)?.takeIf { it.callableReference == call }
                ?: call.treeUpToCall()
        } else call
        val assignment = (qualifiedElement as? KtExpression)
            ?.getAssignmentByLHS()
            ?.takeIf { it.operationToken == KtTokens.EQ }
        val originalDeclaration = analyze(call) {
            //it might resolve in java method which is converted to kotlin by j2k
            //the originalDeclaration in this case should point to the converted non-physical function
            val resolvableCall = (call.parent as? KtCallableReferenceExpression
                ?: call.treeUpToCall()) as? KtResolvableCall
            resolvableCall?.tryResolveCall()?.single?.simple?.symbol?.psi?.navigationElement as? KtDeclaration
                ?: replacement.originalDeclaration
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
            && !elementToBeReplaced.isPartOfCodeFragmentResult()
            && analyze(elementToBeReplaced) { !elementToBeReplaced.isUsedAsExpression }
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
            if (fqName.isRoot) {
                continue
            }

            if (fqName.startsWith(FqName.fromSegments(listOf("kotlin")))) {
                //todo https://youtrack.jetbrains.com/issue/KTIJ-25928
                continue
            }

            if (target?.containingFile == ktFile) {
                continue
            }

            ktFile.addImport(fqName, allUnder, alias)
        }

        var receiver =
            usageExpression?.let { it.getReceiverExpression() ?: (it.parent as? KtCallableReferenceExpression)?.receiverExpression }
        if (receiver is KtSuperExpression) {
            val targetLabel = receiver.getTargetLabel()

            val label = if (targetLabel is KtLabelReferenceExpression) {
                targetLabel.getReferencedName()
            } else {
                val owner = receiver.getStrictParentOfType<KtClassOrObject>()
                if (owner is KtObjectDeclaration && owner.isCompanion() && owner.name == null) {
                    SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT.asString()
                } else {
                    owner?.name
                }
            }
            receiver = psiFactory.createExpression(label?.let { "this@" + it.quoteIfNeeded() } ?: "this")
        }
        receiver?.putCopyableUserData(USER_CODE_KEY, Unit)
        val labelsToAdd = mutableListOf<Pair<KtLambdaExpression, String>>()
        val labelsToReplace = mutableMapOf<String, String>()

        var isReceiverNullable =
            receiver?.let {
                analyze(it) {
                    isNullableType(it.expressionType)
                }
            }

        if (receiver == null) {
            analyze(call) {
                val singleCall =
                    (call as? KtResolvableCall)?.tryResolveCall()?.single?.simple
                val receiverValue = singleCall?.extensionReceiver ?: singleCall?.dispatchReceiver
                if (receiverValue is KaImplicitReceiverValue) {
                    val symbol = receiverValue.symbol
                    val thisText = when {
                        symbol is KaClassSymbol && symbol.classKind.isObject && symbol.name != null -> symbol.name!!.asString()
                        symbol is KaClassifierSymbol && symbol !is KaAnonymousObjectSymbol -> "this@" + symbol.name!!.asString()
                        symbol is KaReceiverParameterSymbol -> {
                            val name = receiverLabelName(
                                symbol.psi as? KtFunctionLiteral,
                                symbol.owningCallableSymbol.callableId?.callableName,
                                labelsToAdd,
                                labelsToReplace
                            )

                            name?.let { "this@$it" } ?: "this"
                        }

                        else -> "this"
                    }
                    receiver = psiFactory.createExpression(thisText)
                    val type = receiverValue.type
                    isReceiverNullable = isNullableType(type)
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

        val introduceValueForParameters = valueParameterInliner.processValueParameterUsages(callableForParameters)

        analyze(call) {
            processTypeParameterUsages(originalDeclaration)
        }

        val lexicalScopeElement = call.parentsWithSelf
            .takeWhile { it !is KtBlockExpression && it !is KtFunction && it !is KtClass && !(it is KtCallableDeclaration && it.parent is KtFile) }
            .last() as KtElement
        val names = mutableSetOf<String>()
        lexicalScopeElement.parent.collectDescendantsOfType<KtProperty>().forEach {
            names.addIfNotNull(it.name)
        }

        val importDeclarations = codeToInline.fqNamesToImport.mapNotNull { importPath ->
            val path = importPath.importPath
            if (path.fqName.isRoot) return@mapNotNull null

            val target =
                psiFactory.createImportDirective(path).mainReference?.resolve() as? KtNamedDeclaration
                    ?: return@mapNotNull null
            importPath to target
        }

        if (elementToBeReplaced is KtSafeQualifiedExpression && isReceiverNullable == true) {
            wrapCodeForSafeCall(receiver!!, isReceiverNullable, elementToBeReplaced)
        } else if (call is KtBinaryExpression && call.operationToken == KtTokens.IDENTIFIER) {
            keepInfixFormIfPossible(importDeclarations.map { it.second })
        }

        codeToInline.convertToCallableReferenceIfNeeded(elementToBeReplaced)
        introduceVariablesForParameters(elementToBeReplaced, receiver, isReceiverNullable, introduceValueForParameters)

        codeToInline.extraComments?.restoreComments(elementToBeReplaced)
        findAndMarkNewDeclarations()
        val performer = when (elementToBeReplaced) {
            is KtExpression -> ExpressionReplacementPerformer(codeToInline, elementToBeReplaced)
            is KtAnnotationEntry -> AnnotationEntryReplacementPerformer(codeToInline, elementToBeReplaced)
            is KtSuperTypeCallEntry -> SuperTypeCallEntryReplacementPerformer(codeToInline, elementToBeReplaced)
            else -> error("Unsupported element: $elementToBeReplaced")
        }
        val labelPointersToAdd = labelsToAdd.map { (expression, labelName) -> expression.createSmartPointer() to labelName }
        return performer.doIt { range ->
            val pointers = range.filterIsInstance<KtElement>().map { it.createSmartPointer() }.toList()
            labelPointersToAdd.forEach { (pointer, labelName) ->
                val expression = pointer.element ?: return@forEach
                if (expression.parent is KtLabeledExpression) return@forEach
                AddLabelUtil.addLabel(expression, labelName).putCopyableUserData(InlineDataKeys.GENERATED_LABEL_KEY, Unit)
            }
            val declarations =
                pointers.mapNotNull { pointer -> pointer.element?.takeIf { it.getCopyableUserData(NEW_DECLARATION_KEY) != null } as? KtNamedDeclaration }
            if (declarations.isNotEmpty()) {
                val endOfScope = pointers.last().element?.endOffset ?: error("Can't find the end of the scope")
                renameDuplicates(declarations, names, endOfScope)
            }
            InlinePostProcessor.postProcessInsertedCode(pointers, commentSaver)
        }
    }

    private fun receiverLabelName(
        functionLiteral: KtFunctionLiteral?,
        callableName: Name?,
        labelsToAdd: MutableList<Pair<KtLambdaExpression, String>>,
        labelsToReplace: MutableMap<String, String>,
    ): String? {
        val lambdaExpression = functionLiteral?.parent as? KtLambdaExpression
        (lambdaExpression?.parent as? KtLabeledExpression)?.getLabelName()?.let { return it }

        val (labelName, callExpression) = functionLiteral?.findLabelAndCall() ?: (callableName to null)
        val name = labelName?.asString() ?: callableName?.asString() ?: return null
        if (callExpression == null || AddLabelUtil.isLabelNameUnique(callExpression, name)) return name
        if (lambdaExpression == null) return name

        val uniqueName = AddLabelUtil.getUniqueLabelName(callExpression, name)
        labelsToAdd += lambdaExpression to uniqueName
        labelsToReplace[name] = uniqueName
        return uniqueName
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
        declaration2Name.forEach { (declaration, newName) ->
            for (reference in ReferencesSearchScopeHelper.search(declaration, LocalSearchScope(declaration.parent)).asIterable()) {
                if (reference.element.startOffset < endOfScope) {
                    reference.handleElementRename(newName)
                }
            }

            declaration.nameIdentifier?.replace(psiFactory.createNameIdentifier(newName))
        }
    }
}

internal fun KtElement.treeUpToCall(): KtElement {
    val userType = parent as? KtUserType ?: return this
    val typeReference = userType.parent as? KtTypeReference ?: return this
    val constructorCalleeExpression = typeReference.parent as? KtConstructorCalleeExpression ?: return this
    val gParent = constructorCalleeExpression.parent
    return gParent as? KtSuperTypeCallEntry ?: gParent as? KtAnnotationEntry ?: this
}

context(_: KaSession)
internal fun isNullableType(type: KaType?): Boolean? {
    if (type == null) return null
    return type.isMarkedNullable || type is KaFlexibleType && type.upperBound.isMarkedNullable
}

private fun KtExpression.isPartOfCodeFragmentResult(): Boolean {
    val result = when (val codeFragment = containingFile) {
        is KtBlockCodeFragment -> codeFragment.getContentElement().statements.lastOrNull()
        is KtExpressionCodeFragment -> codeFragment.getContentElement()
        else -> null
    } ?: return false
    return result.isAncestor(this, strict = false)
}
