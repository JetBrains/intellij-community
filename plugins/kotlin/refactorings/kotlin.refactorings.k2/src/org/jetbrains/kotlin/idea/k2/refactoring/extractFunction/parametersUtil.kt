// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.extractFunction

import com.intellij.psi.PsiMember
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaImplementationDetail
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.KaCallableMemberCall
import org.jetbrains.kotlin.analysis.api.resolution.KaExplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.KaImplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.KaPartiallyAppliedSymbol
import org.jetbrains.kotlin.analysis.api.resolution.KaReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.KaSmartCastedReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.singleCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.components.KaDiagnosticCheckerFilter
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.impl.base.components.KaBaseIllegalPsiException
import org.jetbrains.kotlin.analysis.api.resolution.KaErrorCallInfo
import org.jetbrains.kotlin.analysis.api.signatures.KaCallableSignature
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinDeclarationNameValidator
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester.Companion.suggestNameByName
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggestionProvider
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.AddPrefixReplacement
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.AnalysisResult
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.FqNameReplacement
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.ParametersInfo
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.RenameReplacement
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.ResolveResult
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.ResolvedReferenceInfo
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.TypeDescriptor
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.TypeParameter
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.WrapParameterInWithReplacement
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.collectReferencedTypes
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.collectRelevantConstraints
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.processTypeIfExtractable
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.resolveResult
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtToken
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtCallableReferenceExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtDestructuringDeclarationEntry
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtOperationReferenceExpression
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtSuperExpression
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.psi.KtTypeParameter
import org.jetbrains.kotlin.psi.KtTypeParameterListOwner
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getParentOfTypeAndBranch
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelectorOrThis
import org.jetbrains.kotlin.psi.psiUtil.isInsideOf
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.types.expressions.OperatorConventions
import org.jetbrains.kotlin.idea.k2.refactoring.introduce.K2SemanticMatcher.isSemanticMatch
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.psiUtil.findLabelAndCall
import org.jetbrains.kotlin.utils.addIfNotNull

/**
 * Represents a parameter candidate as it's original declaration and a reference in code.
 *
 * Parameters might be created for class properties, which should be distinguished by their receiver expressions.
 * Otherwise, the same property referenced by different instances would be glued together in one parameter:
 * for <selection>a.foo + b.foo</selection>, two parameters foo1 and foo2 should be created
 */
private class ParameterWithReference(val parameterOrigin: PsiNamedElement, val ref: KtReferenceExpression?) {
    override fun equals(other: Any?): Boolean {
        if (other !is ParameterWithReference) return false
        if (other.parameterOrigin != parameterOrigin) return false
        if (ref == null) return other.ref == null
        return other.ref != null && analyze(ref) { ref.isSemanticMatch(other.ref) }
    }

    override fun hashCode(): Int {
        return parameterOrigin.hashCode()
    }
}

@OptIn(KaExperimentalApi::class)
context(KaSession)
internal fun ExtractionData.inferParametersInfo(
    virtualBlock: KtBlockExpression,
    modifiedVariables: Set<String>,
    typeDescriptor: TypeDescriptor<KaType>,
): ParametersInfo<KaType, MutableParameter> {
    val info = ParametersInfo<KaType, MutableParameter>()

    val extractedDescriptorToParameter = LinkedHashMap<ParameterWithReference, MutableParameter>()

    for (refInfo in getBrokenReferencesInfo(virtualBlock)) {
        val ref = refInfo.refExpr

        val selector = (ref.parent as? KtCallExpression) ?: ref
        val superExpr = (selector.parent as? KtQualifiedExpression)?.receiverExpression as? KtSuperExpression
        if (superExpr != null) {
            info.errorMessage = AnalysisResult.ErrorMessage.SUPER_CALL
            return info
        }

        registerParameter(
            info,
            refInfo,
            extractedDescriptorToParameter,
            false
        )

    }

    val unknownContextParameters = mutableSetOf<KtParameter>()
    analyze(virtualBlock) {
        for (referenceExpression in virtualBlock.collectDescendantsOfType<KtReferenceExpression> { it.resolveResult != null }) {
            val call = referenceExpression.resolveToCall()
            if (call is KaErrorCallInfo) {
                val diagnostic = call.diagnostic
                if (diagnostic is KaFirDiagnostic.NoContextArgument) {
                    unknownContextParameters.addIfNotNull((diagnostic.symbol as? KaContextParameterSymbol)?.psi as? KtParameter)
                }
            }
        }
    }

    unknownContextParameters.forEach {
        val name = it.name ?: "_"
        val parameter = MutableParameter(
            name,
            it.ownerDeclaration as KtNamedDeclaration,
            false,
            it.returnType,
            targetSibling as KtElement,
            contextParameter = true
        )
        parameter.refCount++
        parameter.currentName = name
        info.parameters.add(parameter)
    }

    val varNameValidator = KotlinDeclarationNameValidator(
        commonParent,
        true,
        KotlinNameSuggestionProvider.ValidatorTarget.PARAMETER
    )

    val existingParameterNames = hashSetOf<String>()
    val generateArguments: (KaType) -> List<KaType> =
        { ktType -> (ktType as? KaClassType)?.typeArguments?.mapNotNull { it.type } ?: emptyList() }
    for ((namedElement, parameter) in extractedDescriptorToParameter) {
        if (!parameter
                .parameterType
                .processTypeIfExtractable(
                    info.typeParameters,
                    info.nonDenotableTypes,
                    true,
                    generateArguments,
                    typeDescriptor::isResolvableInScope
                )

        ) {
            continue
        }

        with(parameter) {
            if (currentName == null) {
                currentName = with(KotlinNameSuggester()) {
                    suggestTypeNames(parameterType)
                }.map { nameByType -> suggestNameByName(nameByType) { varNameValidator.validate(it) } }.firstOrNull()
            }

            require(currentName != null || parameter.receiverCandidate)

            if (currentName != null) {
                if ("$currentName" in existingParameterNames) {
                    var index = 0
                    while ("$currentName$index" in existingParameterNames) {
                        index++
                    }
                    currentName = "$currentName$index"
                }
                currentName?.let { existingParameterNames += it }
            } else {
                currentName = "receiver"
            }

            mirrorVarName = if (namedElement.parameterOrigin.name in modifiedVariables) suggestNameByName(
                name
            ) { varNameValidator.validate(it) } else null
            info.parameters.add(this)
        }
    }

    for (typeToCheck in info.typeParameters.flatMap { it.collectReferencedTypes() }.map { it.type }) {
        typeToCheck.processTypeIfExtractable(
            info.typeParameters,
            info.nonDenotableTypes,
            true,
            generateArguments,
            typeDescriptor::isResolvableInScope
        )
    }

    return info
}

context(KaSession)
private fun ExtractionData.registerParameter(
    info: ParametersInfo<KaType, MutableParameter>,
    refInfo: ResolvedReferenceInfo<PsiNamedElement, KtReferenceExpression, KaType>,
    extractedDescriptorToParameter: HashMap<ParameterWithReference, MutableParameter>,
    isMemberExtension: Boolean
) {
    val (originalRef, _, originalDeclaration, resolvedCall) = refInfo.resolveResult

    val partiallyAppliedSymbol =
        resolvedCall?.resolveToCall()?.singleCallOrNull<KaCallableMemberCall<*, *>>()?.partiallyAppliedSymbol
    val dispatchReceiver = partiallyAppliedSymbol?.dispatchReceiver
    val extensionReceiver = partiallyAppliedSymbol?.extensionReceiver
    //Context receivers are not supported.
    //So if both receivers are provided,
    //unresolved conflict is generated by `validate` check and
    //if "Proceed Anyway" is selected, the `extensionReceiver` is chosen to generate partly broken code
    val receiverToExtract =
        extensionReceiver as? KaSmartCastedReceiverValue ?: extensionReceiver as? KaImplicitReceiverValue ?: dispatchReceiver
    val receiverSymbol =
        (((receiverToExtract as? KaSmartCastedReceiverValue)?.original ?: receiverToExtract) as? KaImplicitReceiverValue)?.symbol

    if (receiverSymbol?.psi?.isInsideOf(physicalElements) == true) {
        //receiver is still available
        return
    }

    val thisSymbol = (receiverSymbol as? KaReceiverParameterSymbol)?.owningCallableSymbol ?: receiverSymbol
    val hasThisReceiver = thisSymbol != null && (extensionReceiver != null || thisSymbol.psi?.containingFile == commonParent.containingFile)
    val thisExpr = refInfo.refExpr.parent as? KtThisExpression

    val referencedClassifierSymbol: KaClassifierSymbol? =
        getReferencedClassifierSymbol(thisSymbol, originalDeclaration, refInfo, partiallyAppliedSymbol)

    if (referencedClassifierSymbol != null) {
        registerQualifierReplacements(referencedClassifierSymbol, info, originalDeclaration, originalRef)
    } else {
        val extractThis = (hasThisReceiver && refInfo.smartCast == null) || thisExpr != null
        val extractOrdinaryParameter =
            originalDeclaration is KtDestructuringDeclarationEntry ||
                    originalDeclaration is KtProperty ||
                    originalDeclaration is KtParameter ||
                    originalDeclaration is KtFunctionLiteral

        val extractFunctionRef =
            options.captureLocalFunctions
                    && originalRef is KtSimpleNameExpression
                    && originalRef.getReferencedName() == originalDeclaration.name // to forbid calls by convention
                    && originalDeclaration is KtNamedFunction && originalDeclaration.isLocal

        val elementToExtract = (if (extractThis) thisSymbol?.psi as? PsiNamedElement else null) ?: originalDeclaration

        if (extractThis || extractOrdinaryParameter || extractFunctionRef) {
            val parameterExpression = getParameterArgumentExpression(originalRef, receiverToExtract, refInfo.smartCast)
            val parameter = extractedDescriptorToParameter.getOrPut(ParameterWithReference(elementToExtract, originalRef.takeUnless { extractThis })) {
                val argumentText =
                    calculateArgumentText(
                        hasThisReceiver,
                        extractThis,
                        extractFunctionRef,
                        elementToExtract,
                        thisExpr ?: refInfo.refExpr,
                        originalDeclaration
                    )

                val originalType = createOriginalType(
                    extractFunctionRef,
                    originalDeclaration,
                    parameterExpression,
                    receiverToExtract
                )

                MutableParameter(argumentText, elementToExtract, extractThis, originalType, targetSibling as KtElement, contextParameter = false)
            }

            // TODO add type predicate based on called functions https://youtrack.jetbrains.com/issue/KTIJ-29166
            if (extractFunctionRef) {
                parameter.addTypePredicate(ExactTypePredicate(parameter.parameterType))
            } else if (extractOrdinaryParameter) {
                parameterExpression?.expectedType?.let {
                    parameter.addTypePredicate(SubTypePredicate(it))
                }
            }

            parameter.refCount++

            if (originalRef is KtSimpleNameExpression) {
                //if `originalRef` corresponds to implicit invoke (KtCallExpression), no parameter replacement is required
                if (!extractThis) {
                    parameter.currentName = when (originalDeclaration) {
                        is PsiNameIdentifierOwner -> originalDeclaration.nameIdentifier?.text
                        else -> null
                    }
                }

                // register parameter replacements
                info.originalRefToParameter.putValue(originalRef, parameter)

                val replacement = when {
                    isMemberExtension -> WrapParameterInWithReplacement(parameter)
                    hasThisReceiver && extractThis -> AddPrefixReplacement(parameter)
                    else -> RenameReplacement(parameter)
                }
                info.replacementMap.putValue(originalRef, replacement)
            }
        }
    }
}

private fun getParameterArgumentExpression(
    originalRef: KtReferenceExpression,
    receiverToExtract: KaReceiverValue?,
    smartCast: KaType?
): KtExpression? = when {
    receiverToExtract is KaExplicitReceiverValue -> {
        val receiverExpression = receiverToExtract.expression
        // If p.q has a smart-cast, then extract the entire qualified expression
        if (smartCast != null) receiverExpression.parent as KtExpression else receiverExpression
    }

    receiverToExtract != null && smartCast == null -> null
    else -> (originalRef.parent as? KtThisExpression) ?: originalRef
}

private fun ExtractionData.calculateArgumentText(
    hasThisReceiver: Boolean,
    extractThis: Boolean,
    extractFunctionRef: Boolean,
    elementToExtract: PsiNamedElement,
    argExpr: KtExpression,
    originalDeclaration: PsiNamedElement
): String {
    var argumentText =
        if (hasThisReceiver && extractThis) {
            val label = when {
                elementToExtract is KtFunctionLiteral -> elementToExtract.findLabelAndCall().first
                else -> elementToExtract.name
            }?.let { "@$it" } ?: ""
            "this$label"
        } else {
            val argumentExpr = argExpr.getQualifiedExpressionForSelectorOrThis()
            if (argumentExpr is KtOperationReferenceExpression) {
                val nameElement = argumentExpr.getReferencedNameElement()
                val nameElementType = nameElement.node.elementType
                (nameElementType as? KtToken)?.let {
                    OperatorConventions.getNameForOperationSymbol(it)?.asString()
                } ?: nameElement.text
            } else argumentExpr.text
                ?: throw AssertionError("reference shouldn't be empty: code fragment = $codeFragmentText")
        }
    if (extractFunctionRef) {
        val receiverTypeText = (originalDeclaration as KtCallableDeclaration).receiverTypeReference?.text ?: ""
        argumentText = "$receiverTypeText::$argumentText"
    }
    return argumentText
}

/**
 * Register replacements which expand locally available types to FQ names if possible.
 */
context(KaSession)
private fun ExtractionData.registerQualifierReplacements(
    referencedClassifierSymbol: KaClassifierSymbol,
    parametersInfo: ParametersInfo<KaType, MutableParameter>,
    originalDeclaration: PsiNamedElement,
    originalRef: KtReferenceExpression
) {
    if (referencedClassifierSymbol is KaTypeParameterSymbol) {
        val typeParameter = referencedClassifierSymbol.psi as KtTypeParameter
        val listOwner = typeParameter.parentOfType<KtTypeParameterListOwner>()
        if (listOwner == null || !PsiTreeUtil.isAncestor(listOwner, targetSibling, true)) {
            parametersInfo.typeParameters.add(TypeParameter(typeParameter, typeParameter.collectRelevantConstraints()))
        }
    } else if (referencedClassifierSymbol is KaClassSymbol && originalRef is KtSimpleNameExpression) {
        val fqName = referencedClassifierSymbol.classId?.asSingleFqName()
        if (fqName != null) {
            val name = when (originalDeclaration) {
                is KtConstructor<*> -> null
                is KtPropertyAccessor -> originalDeclaration.property.name
                is KtClassOrObject -> null
                else -> originalDeclaration.name
            }
            val fqNameChild = if (name != null) fqName.child(Name.identifier(name)) else fqName
            parametersInfo.replacementMap.putValue(originalRef, FqNameReplacement(fqNameChild))
        } else {
            parametersInfo.nonDenotableTypes.add(buildClassType(referencedClassifierSymbol))
        }
    }
}

context(KaSession)
private fun getReferencedClassifierSymbol(
    thisSymbol: KaSymbol?,
    originalDeclaration: PsiNamedElement,
    refInfo: ResolvedReferenceInfo<PsiNamedElement, KtReferenceExpression, KaType>,
    partiallyAppliedSymbol: KaPartiallyAppliedSymbol<KaCallableSymbol, KaCallableSignature<KaCallableSymbol>>?
): KaClassifierSymbol? {
    if (partiallyAppliedSymbol?.symbol is KaNamedFunctionSymbol && originalDeclaration is KtConstructor<*>) {
        // dataClass.copy(): do not replace with call to constructor
        return null
    }
    val referencedSymbol = (thisSymbol ?: (originalDeclaration as? KtNamedDeclaration)?.symbol
    ?: (originalDeclaration as? PsiMember)?.callableSymbol) ?: return null
    return when (referencedSymbol) {
        is KaClassSymbol -> when (referencedSymbol.classKind) {
            KaClassKind.OBJECT, KaClassKind.COMPANION_OBJECT, KaClassKind.ENUM_CLASS -> referencedSymbol
            //if type reference or call to implicit constructor, then type expansion might be required
            else -> if (refInfo.refExpr.getNonStrictParentOfType<KtTypeReference>() != null || partiallyAppliedSymbol?.symbol is KaConstructorSymbol) referencedSymbol else null
        }

        is KaTypeParameterSymbol -> referencedSymbol

        is KaConstructorSymbol -> referencedSymbol.containingDeclaration as? KaClassifierSymbol

        else -> null
    }
}

context(KaSession)
@OptIn(KaExperimentalApi::class, KaImplementationDetail::class)
private fun createOriginalType(
    extractFunctionRef: Boolean,
    originalDeclaration: PsiNamedElement,
    parameterExpression: KtExpression?,
    receiverToExtract: KaReceiverValue?
): KaType = (if (extractFunctionRef) {
    val functionSymbol = (originalDeclaration as KtNamedFunction).symbol as KaNamedFunctionSymbol
    val typeString =
        buildString { //todo rewrite as soon as functional type can be created by api call: https://youtrack.jetbrains.com/issue/KT-66566
            functionSymbol.receiverParameter?.returnType?.render(position = Variance.INVARIANT)?.let {
                append(it)
                append(".")
            }
            functionSymbol.valueParameters.joinTo(
                this,
                ", ",
                "(",
                ")"
            ) { //names provided here are removed due to https://youtrack.jetbrains.com/issue/KT-65846
                it.name.asString() + ": " + it.returnType.render(position = Variance.INVARIANT)
            }

            append(" -> ")
            append(functionSymbol.returnType.render(position = Variance.INVARIANT))
        }

    val contentElement =
        KtPsiFactory(originalDeclaration.project).createTypeCodeFragment(typeString, originalDeclaration).getContentElement()
    if (contentElement != null) {
        analyze(contentElement) { contentElement.type.createPointer() }.restore(this@KaSession)
    } else null

} else {
    parameterExpression?.expressionType ?: receiverToExtract?.type
}) ?: builtinTypes.nullableAny


@OptIn(KaExperimentalApi::class)
private fun ExtractionData.getBrokenReferencesInfo(body: KtBlockExpression): List<ResolvedReferenceInfo<PsiNamedElement, KtReferenceExpression, KaType>> {
    val newReferences = body.collectDescendantsOfType<KtReferenceExpression> { it.resolveResult != null }

    val smartCastPossibleRoots = mutableSetOf<KtExpression>()
    val referencesInfo = ArrayList<ResolvedReferenceInfo<PsiNamedElement, KtReferenceExpression, KaType>>()
    for (newRef in newReferences) {
        val originalResolveResult = newRef.resolveResult as? ResolveResult<PsiNamedElement, KtReferenceExpression> ?: continue
        val originalRefExpr = originalResolveResult.originalRefExpr
        val parent = newRef.parent

        val smartCast: KaType?

        fun calculateSmartCastType(target: KtExpression): KaType? {
            return analyze(target) {
                val cast = target.smartCastInfo?.smartCastType
                when {
                    cast == null -> {
                        smartCastPossibleRoots.add(target)
                        null
                    }

                    //same qualified expressions without smartcast are present in the code fragment,
                    //so smart cast is done inside selection, no need to extract additional parameter
                    smartCastPossibleRoots.any { it.isSemanticMatch(target) } -> null
                    else -> cast
                }
            }
        }

        val possibleTypes: Set<KaType>

        // Qualified property reference: a.b
        val qualifiedExpression = newRef.getQualifiedExpressionForSelector()
        if (qualifiedExpression != null) {
            val smartCastTarget = originalRefExpr.parent as KtExpression
            smartCast = calculateSmartCastType(smartCastTarget)
            possibleTypes = analyze(smartCastTarget) { smartCastTarget.expectedType?.let { setOf(it) } ?: emptySet() }
            val (isCompanionObject, bothReceivers) = analyze(smartCastTarget) {
                val symbol = originalRefExpr.resolveToCall()?.singleCallOrNull<KaCallableMemberCall<*, *>>()?.partiallyAppliedSymbol
                val receiverSymbol = (symbol?.dispatchReceiver as? KaImplicitReceiverValue)?.symbol
                ((receiverSymbol?.containingDeclaration as? KaClassSymbol)?.classKind == KaClassKind.COMPANION_OBJECT) to
                        (symbol?.dispatchReceiver != null && symbol.extensionReceiver != null)
            }
            val shouldSkipPrimaryReceiver = smartCast == null
                    && !isCompanionObject
                    && qualifiedExpression.receiverExpression !is KtSuperExpression
            if (shouldSkipPrimaryReceiver && !bothReceivers) continue
        } else {
            if (newRef.getParentOfTypeAndBranch<KtCallableReferenceExpression> { callableReference } != null) continue
            // Qualified functional property reference: a.*b*()
            if (originalResolveResult.descriptor is KtProperty && parent is KtCallExpression && parent.calleeExpression == newRef && parent.getQualifiedExpressionForSelector() != null) {
                continue
            }
            smartCast = calculateSmartCastType(originalRefExpr)
            possibleTypes = analyze(originalRefExpr) { originalRefExpr.expectedType?.let { setOf(it) } ?: emptySet() }
        }

        // Skip P in type references like 'P.Q'
        if (parent is KtUserType && (parent.parent as? KtUserType)?.qualifier == parent) continue

        val descriptor = newRef.mainReference.resolve()
        val originalDescriptor = originalRefExpr.mainReference.resolve()
        val isBadRef = descriptor != originalDescriptor

        //if resolves to the same element in copy, then no additional parameter is required
        if (isBadRef &&
            descriptor != null && originalDescriptor != null &&
            originalDescriptor.getCopyableUserData(targetKey) == descriptor.getCopyableUserData(targetKey)
        ) {
            continue
        }

        fun hasResolveErrors(): Boolean =
            analyze(newRef) { newRef.diagnostics(KaDiagnosticCheckerFilter.ONLY_COMMON_CHECKERS) }
                .any {
                    it.diagnosticClass == KaFirDiagnostic.UnresolvedReferenceWrongReceiver::class ||
                            it.diagnosticClass == KaFirDiagnostic.UnresolvedReference::class
                }
        if ((isBadRef || hasResolveErrors() || smartCast != null) &&
            !originalResolveResult.declaration.isInsideOf(physicalElements)
        ) {
            referencesInfo.add(
                ResolvedReferenceInfo(
                    newRef,
                    originalResolveResult,
                    smartCast,
                    possibleTypes,
                )
            )
        }
    }

    return referencesInfo
}