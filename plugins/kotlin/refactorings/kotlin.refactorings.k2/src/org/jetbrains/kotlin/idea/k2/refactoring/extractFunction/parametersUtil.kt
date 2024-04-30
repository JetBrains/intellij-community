// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.extractFunction

import com.intellij.psi.PsiMember
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.calls.KtCallableMemberCall
import org.jetbrains.kotlin.analysis.api.calls.KtExplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.calls.KtImplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.calls.KtPartiallyAppliedSymbol
import org.jetbrains.kotlin.analysis.api.calls.KtReceiverValue
import org.jetbrains.kotlin.analysis.api.calls.KtSmartCastedReceiverValue
import org.jetbrains.kotlin.analysis.api.calls.singleCallOrNull
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.analysis.api.components.KtDiagnosticCheckerFilter
import org.jetbrains.kotlin.analysis.api.components.buildClassType
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.analysis.api.signatures.KtCallableSignature
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KtClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtClassifierSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtReceiverParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtTypeParameterSymbol
import org.jetbrains.kotlin.analysis.api.types.KtNonErrorClassType
import org.jetbrains.kotlin.analysis.api.types.KtType
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
import org.jetbrains.kotlin.psi.KtReferenceExpression

context(KtAnalysisSession)
internal fun ExtractionData.inferParametersInfo(
    virtualBlock: KtBlockExpression,
    modifiedVariables: Set<String>,
    typeDescriptor: TypeDescriptor<KtType>,
): ParametersInfo<KtType, MutableParameter> {
    val info = ParametersInfo<KtType, MutableParameter>()

    val extractedDescriptorToParameter = LinkedHashMap<PsiNamedElement, MutableParameter>()

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

    val varNameValidator = KotlinDeclarationNameValidator(
        commonParent,
        true,
        KotlinNameSuggestionProvider.ValidatorTarget.PARAMETER
    )

    val existingParameterNames = hashSetOf<String>()
    val generateArguments: (KtType) -> List<KtType> =
        { ktType -> (ktType as? KtNonErrorClassType)?.ownTypeArguments?.mapNotNull { it.type } ?: emptyList() }
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

            mirrorVarName = if (namedElement.name in modifiedVariables) suggestNameByName(
                name
            ) { varNameValidator.validate(it) } else null
            info.parameters.add(this)
        }
    }

    for (typeToCheck in info.typeParameters.flatMap { it.collectReferencedTypes() }.map { it.getKtType() } ) {
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

context(KtAnalysisSession)
private fun ExtractionData.registerParameter(
    info: ParametersInfo<KtType, MutableParameter>,
    refInfo: ResolvedReferenceInfo<PsiNamedElement, KtReferenceExpression, KtType>,
    extractedDescriptorToParameter: HashMap<PsiNamedElement, MutableParameter>,
    isMemberExtension: Boolean
) {
    val (originalRef, _, originalDeclaration, resolvedCall) = refInfo.resolveResult

    val partiallyAppliedSymbol =
        resolvedCall?.resolveCall()?.singleCallOrNull<KtCallableMemberCall<*, *>>()?.partiallyAppliedSymbol
    val dispatchReceiver = partiallyAppliedSymbol?.dispatchReceiver
    val extensionReceiver = partiallyAppliedSymbol?.extensionReceiver
    //Context receivers are not supported.
    //So if both receivers are provided,
    //unresolved conflict is generated by `validate` check and
    //if "Proceed Anyway" is selected, the `extensionReceiver` is chosen to generate partly broken code
    val receiverToExtract =
        extensionReceiver as? KtSmartCastedReceiverValue ?: extensionReceiver as? KtImplicitReceiverValue ?: dispatchReceiver
    val receiverSymbol =
        (((receiverToExtract as? KtSmartCastedReceiverValue)?.original ?: receiverToExtract) as? KtImplicitReceiverValue)?.symbol

    if (receiverSymbol?.psi?.isInsideOf(physicalElements) == true) {
        //receiver is still available
        return
    }

    val thisSymbol = (receiverSymbol as? KtReceiverParameterSymbol)?.type?.expandedClassSymbol ?: receiverSymbol
    val hasThisReceiver = thisSymbol != null
    val thisExpr = refInfo.refExpr.parent as? KtThisExpression

    val referencedClassifierSymbol: KtClassifierSymbol? =
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
            val parameter = extractedDescriptorToParameter.getOrPut(elementToExtract) {
                var argumentText =
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

                MutableParameter(argumentText, elementToExtract, extractThis, originalType, targetSibling as KtElement)
            }

            // TODO add type predicate based on called functions https://youtrack.jetbrains.com/issue/KTIJ-29166
            if (extractFunctionRef) {
                parameter.addTypePredicate(ExactTypePredicate(parameter.parameterType))
            } else if (extractOrdinaryParameter) {
                parameterExpression?.getExpectedType()?.let {
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
    receiverToExtract: KtReceiverValue?,
    smartCast: KtType?
): KtExpression? = when {
    receiverToExtract is KtExplicitReceiverValue -> {
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
            val label = elementToExtract.name?.let { "@$it" } ?: ""
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
context(KtAnalysisSession)
private fun ExtractionData.registerQualifierReplacements(
    referencedClassifierSymbol: KtClassifierSymbol,
    parametersInfo: ParametersInfo<KtType, MutableParameter>,
    originalDeclaration: PsiNamedElement,
    originalRef: KtReferenceExpression
) {
    if (referencedClassifierSymbol is KtTypeParameterSymbol) {
        val typeParameter = referencedClassifierSymbol.psi as KtTypeParameter
        val listOwner = typeParameter.parentOfType<KtTypeParameterListOwner>()
        if (listOwner == null || !PsiTreeUtil.isAncestor(listOwner, targetSibling, true)) {
            parametersInfo.typeParameters.add(TypeParameter(typeParameter, typeParameter.collectRelevantConstraints()))
        }
    } else if (referencedClassifierSymbol is KtClassOrObjectSymbol && originalRef is KtSimpleNameExpression) {
        val fqName = referencedClassifierSymbol.classIdIfNonLocal?.asSingleFqName()
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

context(KtAnalysisSession)
private fun getReferencedClassifierSymbol(
    thisSymbol: KtSymbol?,
    originalDeclaration: PsiNamedElement,
    refInfo: ResolvedReferenceInfo<PsiNamedElement, KtReferenceExpression, KtType>,
    partiallyAppliedSymbol: KtPartiallyAppliedSymbol<KtCallableSymbol, KtCallableSignature<KtCallableSymbol>>?
): KtClassifierSymbol? {
    val referencedSymbol = (thisSymbol ?: (originalDeclaration as? KtNamedDeclaration)?.getSymbol()
    ?: (originalDeclaration as? PsiMember)?.getCallableSymbol()) ?: return null
    return when (referencedSymbol) {
        is KtClassOrObjectSymbol -> when (referencedSymbol.classKind) {
            KtClassKind.OBJECT, KtClassKind.COMPANION_OBJECT, KtClassKind.ENUM_CLASS -> referencedSymbol
            //if type reference or call to implicit constructor, then type expansion might be required
            else -> if (refInfo.refExpr.getNonStrictParentOfType<KtTypeReference>() != null || partiallyAppliedSymbol?.symbol is KtConstructorSymbol) referencedSymbol else null
        }

        is KtTypeParameterSymbol -> referencedSymbol

        is KtConstructorSymbol -> referencedSymbol.getContainingSymbol() as? KtClassifierSymbol

        else -> null
    }
}

context(KtAnalysisSession)
private fun createOriginalType(
    extractFunctionRef: Boolean,
    originalDeclaration: PsiNamedElement,
    parameterExpression: KtExpression?,
    receiverToExtract: KtReceiverValue?
): KtType = (if (extractFunctionRef) {
    val functionSymbol = (originalDeclaration as KtNamedFunction).getSymbol() as KtFunctionSymbol
    val typeString =
        buildString { //todo rewrite as soon as functional type can be created by api call: https://youtrack.jetbrains.com/issue/KT-66566
            functionSymbol.receiverParameter?.type?.render(position = Variance.INVARIANT)?.let {
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

    org.jetbrains.kotlin.psi.KtPsiFactory(originalDeclaration.project).createTypeCodeFragment(typeString, originalDeclaration)
        .getContentElement()?.getKtType()
} else {
    parameterExpression?.getKtType() ?: receiverToExtract?.type
}) ?: builtinTypes.NULLABLE_ANY


private fun ExtractionData.getBrokenReferencesInfo(body: KtBlockExpression): List<ResolvedReferenceInfo<PsiNamedElement, KtReferenceExpression, KtType>> {
    val newReferences = body.collectDescendantsOfType<KtReferenceExpression> { it.resolveResult != null }

    val smartCastPossibleRoots = mutableSetOf<KtExpression>()
    val referencesInfo = ArrayList<ResolvedReferenceInfo<PsiNamedElement, KtReferenceExpression, KtType>>()
    for (newRef in newReferences) {
        val originalResolveResult = newRef.resolveResult as? ResolveResult<PsiNamedElement, KtReferenceExpression> ?: continue
        val originalRefExpr = originalResolveResult.originalRefExpr
        val parent = newRef.parent

        val smartCast: KtType?

        fun calculateSmartCastType(target: KtExpression): KtType? {
            return analyze(target) {
                val cast = target.getSmartCastInfo()?.smartCastType
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

        val possibleTypes: Set<KtType>

        // Qualified property reference: a.b
        val qualifiedExpression = newRef.getQualifiedExpressionForSelector()
        if (qualifiedExpression != null) {
            val smartCastTarget = originalRefExpr.parent as KtExpression
            smartCast = calculateSmartCastType(smartCastTarget)
            possibleTypes = analyze(smartCastTarget) { smartCastTarget.getExpectedType()?.let { setOf(it) } ?: emptySet() }
            val (isCompanionObject, bothReceivers) = analyze(smartCastTarget) {
                val symbol = originalRefExpr.resolveCall()?.singleCallOrNull<KtCallableMemberCall<*, *>>()?.partiallyAppliedSymbol
                val receiverSymbol = (symbol?.dispatchReceiver as? KtImplicitReceiverValue)?.symbol
                ((receiverSymbol?.getContainingSymbol() as? KtClassOrObjectSymbol)?.classKind == KtClassKind.COMPANION_OBJECT) to
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
            possibleTypes = analyze(originalRefExpr) { originalRefExpr.getExpectedType()?.let { setOf(it) } ?: emptySet() }
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
            analyze(newRef) { newRef.getDiagnostics(KtDiagnosticCheckerFilter.ONLY_COMMON_CHECKERS) }
                .any {
                    it.diagnosticClass == KtFirDiagnostic.UnresolvedReferenceWrongReceiver::class ||
                            it.diagnosticClass == KtFirDiagnostic.UnresolvedReference::class
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