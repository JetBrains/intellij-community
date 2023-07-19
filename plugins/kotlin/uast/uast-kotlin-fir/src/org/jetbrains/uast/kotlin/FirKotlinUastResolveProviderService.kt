// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.uast.kotlin

import com.intellij.psi.*
import com.intellij.psi.impl.compiled.ClsMemberImpl
import com.intellij.psi.impl.file.PsiPackageImpl
import com.intellij.util.SmartList
import org.jetbrains.kotlin.analysis.api.base.KtConstantValue
import org.jetbrains.kotlin.analysis.api.calls.*
import org.jetbrains.kotlin.analysis.api.components.KtConstantEvaluationMode
import org.jetbrains.kotlin.analysis.api.components.buildClassType
import org.jetbrains.kotlin.analysis.api.components.buildTypeParameterType
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtNamedSymbol
import org.jetbrains.kotlin.analysis.api.types.*
import org.jetbrains.kotlin.analysis.project.structure.KtLibraryModule
import org.jetbrains.kotlin.analysis.project.structure.KtSourceModule
import org.jetbrains.kotlin.analysis.project.structure.ProjectStructureProvider
import org.jetbrains.kotlin.asJava.toLightAnnotation
import org.jetbrains.kotlin.asJava.toLightElements
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.uast.*
import org.jetbrains.uast.analysis.KotlinExtensionConstants.LAMBDA_THIS_PARAMETER_NAME
import org.jetbrains.uast.kotlin.internal.*
import org.jetbrains.uast.kotlin.psi.UastKotlinPsiParameterBase

interface FirKotlinUastResolveProviderService : BaseKotlinUastResolveProviderService {

    override val languagePlugin: UastLanguagePlugin
        get() = firKotlinUastPlugin

    override val baseKotlinConverter: BaseKotlinConverter
        get() = FirKotlinConverter

    private val KtExpression.parentValueArgument: ValueArgument?
        get() = parents.firstOrNull { it is ValueArgument } as? ValueArgument

    fun isSupportedElement(psiElement: PsiElement): Boolean

    override fun convertToPsiAnnotation(ktElement: KtElement): PsiAnnotation? {
        return ktElement.toLightAnnotation()
    }

    override fun convertValueArguments(ktCallElement: KtCallElement, parent: UElement): List<UNamedExpression>? {
        analyzeForUast(ktCallElement) {
            val argumentMapping = ktCallElement.resolveCall()?.singleFunctionCallOrNull()?.argumentMapping ?: return null
            val handledParameters = mutableSetOf<KtValueParameterSymbol>()
            val valueArguments = SmartList<UNamedExpression>()
            // NB: we need a loop over call element's value arguments to preserve their order.
            ktCallElement.valueArguments.forEach {
                val parameter = argumentMapping[it.getArgumentExpression()]?.symbol ?: return@forEach
                if (!handledParameters.add(parameter)) return@forEach
                val arguments = argumentMapping.entries
                    .filter { (_, param) -> param.symbol == parameter }
                    .mapNotNull { (arg, _) -> arg.parentValueArgument }
                val name = parameter.name.asString()
                when {
                    arguments.size == 1 ->
                        KotlinUNamedExpression.create(name, arguments.first(), parent)
                    arguments.size > 1 ->
                        KotlinUNamedExpression.create(name, arguments, parent)
                    else -> null
                }?.let { valueArgument -> valueArguments.add(valueArgument) }
            }
            return valueArguments.ifEmpty { null }
        }
    }

    override fun findAttributeValueExpression(uAnnotation: KotlinUAnnotation, arg: ValueArgument): UExpression? {
        val annotationEntry = uAnnotation.sourcePsi
        analyzeForUast(annotationEntry) {
            val resolvedAnnotationCall = annotationEntry.resolveCall()?.singleCallOrNull<KtAnnotationCall>() ?: return null
            val parameter = resolvedAnnotationCall.argumentMapping[arg.getArgumentExpression()]?.symbol ?: return null
            val namedExpression = uAnnotation.attributeValues.find { it.name == parameter.name.asString() }
            return namedExpression?.expression as? KotlinUVarargExpression ?: namedExpression
        }
    }

    override fun findDefaultValueForAnnotationAttribute(ktCallElement: KtCallElement, name: String): KtExpression? {
        analyzeForUast(ktCallElement) {
            val resolvedAnnotationConstructorSymbol =
                ktCallElement.resolveCall()?.singleConstructorCallOrNull()?.symbol ?: return null
            val parameter = resolvedAnnotationConstructorSymbol.valueParameters.find { it.name.asString() == name } ?: return null
            return (parameter.psi as? KtParameter)?.defaultValue
        }
    }

    override fun getArgumentForParameter(ktCallElement: KtCallElement, index: Int, parent: UElement): UExpression? {
        analyzeForUast(ktCallElement) {
            val resolvedFunctionCall = ktCallElement.resolveCall()?.singleFunctionCallOrNull()
            val resolvedFunctionLikeSymbol =
                resolvedFunctionCall?.symbol ?: return null
            val parameter = resolvedFunctionLikeSymbol.valueParameters.getOrNull(index) ?: return null
            val arguments = resolvedFunctionCall.argumentMapping.entries
                .filter { (_, param) -> param.symbol == parameter }
                .mapNotNull { (arg, _) -> arg.parentValueArgument }
            return when {
                arguments.isEmpty() -> null
                arguments.size == 1 -> {
                    val argument = arguments.single()
                    if (parameter.isVararg && argument.getSpreadElement() == null)
                        baseKotlinConverter.createVarargsHolder(arguments, parent)
                    else
                        baseKotlinConverter.convertOrEmpty(argument.getArgumentExpression(), parent)
                }
                else ->
                    baseKotlinConverter.createVarargsHolder(arguments, parent)
            }
        }
    }

    override fun getImplicitReturn(ktLambdaExpression: KtLambdaExpression, parent: UElement): KotlinUImplicitReturnExpression? {
        val lastExpression = ktLambdaExpression.bodyExpression?.statements?.lastOrNull() ?: return null
        // Skip _explicit_ return.
        if (lastExpression is KtReturnExpression) return null
        analyzeForUast(ktLambdaExpression) {
            // TODO: Should check an explicit, expected return type as well
            //  e.g., val y: () -> Unit = { 1 } // the lambda return type is Int, but we won't add an implicit return here.
            val returnType = ktLambdaExpression.functionLiteral.getAnonymousFunctionSymbol().returnType
            val returnUnitOrNothing = returnType.isUnit || returnType.isNothing
            return if (returnUnitOrNothing) null else
                KotlinUImplicitReturnExpression(parent).apply {
                    returnExpression = baseKotlinConverter.convertOrEmpty(lastExpression, this)
                }
        }
    }

    override fun getImplicitParameters(
        ktLambdaExpression: KtLambdaExpression,
        parent: UElement,
        includeExplicitParameters: Boolean
    ): List<KotlinUParameter> {
        analyzeForUast(ktLambdaExpression) {
            val anonymousFunctionSymbol = ktLambdaExpression.functionLiteral.getAnonymousFunctionSymbol()
            val parameters = mutableListOf<KotlinUParameter>()
            if (includeExplicitParameters && anonymousFunctionSymbol.receiverParameter != null) {
                val lambdaImplicitReceiverType = anonymousFunctionSymbol.receiverParameter!!.type.asPsiType(
                    ktLambdaExpression,
                    allowErrorTypes = false,
                    KtTypeMappingMode.DEFAULT_UAST,
                    isAnnotationMethod = false
                ) ?: UastErrorType
                parameters.add(
                    KotlinUParameter(
                        UastKotlinPsiParameterBase(
                            name = LAMBDA_THIS_PARAMETER_NAME,
                            type = lambdaImplicitReceiverType,
                            parent = ktLambdaExpression,
                            ktOrigin = ktLambdaExpression,
                            language = ktLambdaExpression.language,
                            isVarArgs = false,
                            ktDefaultValue = null
                        ),
                        sourcePsi = null,
                        parent
                    )
                )
            }
            anonymousFunctionSymbol.valueParameters.mapTo(parameters) { p ->
                val psiType = p.returnType.asPsiType(
                    ktLambdaExpression,
                    allowErrorTypes = false,
                    KtTypeMappingMode.DEFAULT_UAST,
                    isAnnotationMethod = false
                ) ?: UastErrorType
                KotlinUParameter(
                    UastKotlinPsiParameterBase(
                        name = p.name.asString(),
                        type = psiType,
                        parent = ktLambdaExpression,
                        ktOrigin = ktLambdaExpression,
                        language = ktLambdaExpression.language,
                        isVarArgs = p.isVararg,
                        ktDefaultValue = null
                    ),
                    null,
                    parent
                )
            }
            return parameters
        }
    }

    override fun getPsiAnnotations(psiElement: PsiModifierListOwner): Array<PsiAnnotation> {
        return psiElement.annotations
    }

    override fun getReferenceVariants(ktExpression: KtExpression, nameHint: String): Sequence<PsiElement> {
        analyzeForUast(ktExpression) {
            val candidates = ktExpression.collectCallCandidates()
            if (candidates.isEmpty()) return emptySequence()
            return sequence {
                candidates.forEach { candidateInfo ->
                    analyzeForUast(ktExpression) {
                        when (val candidate = candidateInfo.candidate) {
                            is KtFunctionCall<*> -> {
                                toPsiMethod(candidate.partiallyAppliedSymbol.symbol, ktExpression)?.let { yield(it) }
                            }

                            is KtCompoundVariableAccessCall -> {
                                val variableSymbol = candidate.partiallyAppliedSymbol.symbol
                                if (variableSymbol is KtSyntheticJavaPropertySymbol) {
                                    toPsiMethod(variableSymbol.getter, ktExpression)?.let { yield(it) }
                                    variableSymbol.setter?.let { toPsiMethod(it, ktExpression) }?.let { yield(it) }
                                } else {
                                    psiForUast(variableSymbol, ktExpression.project)?.let { yield(it) }
                                }
                                toPsiMethod(
                                    candidate.compoundAccess.operationPartiallyAppliedSymbol.symbol,
                                    ktExpression
                                )?.let { yield(it) }
                            }

                            is KtCompoundArrayAccessCall -> {
                                toPsiMethod(candidate.getPartiallyAppliedSymbol.symbol, ktExpression)?.let { yield(it) }
                                toPsiMethod(candidate.setPartiallyAppliedSymbol.symbol, ktExpression)?.let { yield(it) }
                                toPsiMethod(
                                    candidate.compoundAccess.operationPartiallyAppliedSymbol.symbol,
                                    ktExpression
                                )?.let { yield(it) }
                            }

                            else -> {}
                        }
                    }
                }
            }
        }
    }

    override fun resolveBitwiseOperators(ktBinaryExpression: KtBinaryExpression): UastBinaryOperator {
        val other = UastBinaryOperator.OTHER
        analyzeForUast(ktBinaryExpression) {
            val resolvedCall = ktBinaryExpression.resolveCall()?.singleFunctionCallOrNull() ?: return other
            val operatorName = resolvedCall.symbol.callableIdIfNonLocal?.callableName?.asString() ?: return other
            return KotlinUBinaryExpression.BITWISE_OPERATORS[operatorName] ?: other
        }
    }

    override fun resolveCall(ktElement: KtElement): PsiMethod? {
        analyzeForUast(ktElement) {
            val ktCallInfo = ktElement.resolveCall() ?: return null
            ktCallInfo.singleFunctionCallOrNull()
                ?.symbol
                ?.let { return toPsiMethod(it, ktElement) }
            return when (ktElement) {
                is KtPrefixExpression,
                is KtPostfixExpression -> {
                    ktCallInfo.singleCallOrNull<KtCompoundVariableAccessCall>()
                        ?.compoundAccess
                        ?.operationPartiallyAppliedSymbol
                        ?.signature
                        ?.symbol
                        ?.let { toPsiMethod(it, ktElement) }
                }
                else -> null
            }
        }
    }

    override fun resolveSyntheticJavaPropertyAccessorCall(ktSimpleNameExpression: KtSimpleNameExpression): PsiMethod? {
        return analyzeForUast(ktSimpleNameExpression) {
            val variableAccessCall = ktSimpleNameExpression.resolveCall()?.singleCallOrNull<KtSimpleVariableAccessCall>() ?: return null
            val propertySymbol = variableAccessCall.symbol as? KtSyntheticJavaPropertySymbol?: return null
            when (variableAccessCall.simpleAccess) {
                is KtSimpleVariableAccess.Read ->
                    toPsiMethod(propertySymbol.getter, ktSimpleNameExpression)
                is KtSimpleVariableAccess.Write ->
                    toPsiMethod(propertySymbol.setter ?: return null, ktSimpleNameExpression)
            }
        }
    }

    override fun isResolvedToExtension(ktCallElement: KtCallElement): Boolean {
        analyzeForUast(ktCallElement) {
            val ktCall = ktCallElement.resolveCall()?.singleFunctionCallOrNull() ?: return false
            return ktCall.symbol.isExtension
        }
    }

    override fun resolvedFunctionName(ktCallElement: KtCallElement): String? {
        analyzeForUast(ktCallElement) {
            val resolvedFunctionLikeSymbol = ktCallElement.resolveCall()?.singleFunctionCallOrNull()?.symbol ?: return null
            return (resolvedFunctionLikeSymbol as? KtNamedSymbol)?.name?.identifierOrNullIfSpecial
                ?: (resolvedFunctionLikeSymbol as? KtConstructorSymbol)?.let { SpecialNames.INIT.asString() }
        }
    }

    override fun qualifiedAnnotationName(ktCallElement: KtCallElement): String? {
        analyzeForUast(ktCallElement) {
            val resolvedAnnotationConstructorSymbol =
                ktCallElement.resolveCall()?.singleConstructorCallOrNull()?.symbol ?: return null
            return resolvedAnnotationConstructorSymbol.containingClassIdIfNonLocal
                ?.asSingleFqName()
                ?.toString()
        }
    }

    override fun callKind(ktCallElement: KtCallElement): UastCallKind {
        analyzeForUast(ktCallElement) {
            val resolvedFunctionLikeSymbol =
                ktCallElement.resolveCall()?.singleFunctionCallOrNull()?.symbol ?: return UastCallKind.METHOD_CALL
            val fqName = resolvedFunctionLikeSymbol.callableIdIfNonLocal?.asSingleFqName()
            return when {
                resolvedFunctionLikeSymbol is KtSamConstructorSymbol ||
                resolvedFunctionLikeSymbol is KtConstructorSymbol -> UastCallKind.CONSTRUCTOR_CALL
                fqName != null && isAnnotationArgumentArrayInitializer(ktCallElement, fqName) -> UastCallKind.NESTED_ARRAY_INITIALIZER
                else -> UastCallKind.METHOD_CALL
            }
        }
    }

    override fun isAnnotationConstructorCall(ktCallElement: KtCallElement): Boolean {
        analyzeForUast(ktCallElement) {
            val resolvedAnnotationConstructorSymbol =
                ktCallElement.resolveCall()?.singleConstructorCallOrNull()?.symbol ?: return false
            val ktType = resolvedAnnotationConstructorSymbol.returnType
            val context = containingKtClass(resolvedAnnotationConstructorSymbol) ?: ktCallElement
            val psiClass = toPsiClass(ktType, null, context, ktCallElement.typeOwnerKind) ?: return false
            return psiClass.isAnnotationType
        }
    }

    override fun resolveToClassIfConstructorCall(ktCallElement: KtCallElement, source: UElement): PsiClass? {
        analyzeForUast(ktCallElement) {
            val resolvedFunctionLikeSymbol = ktCallElement.resolveCall()?.singleFunctionCallOrNull()?.symbol ?: return null
            return when (resolvedFunctionLikeSymbol) {
                is KtConstructorSymbol -> {
                    val context = containingKtClass(resolvedFunctionLikeSymbol) ?: ktCallElement
                    toPsiClass(resolvedFunctionLikeSymbol.returnType, source, context, ktCallElement.typeOwnerKind)
                }
                is KtSamConstructorSymbol -> {
                    toPsiClass(resolvedFunctionLikeSymbol.returnType, source, ktCallElement, ktCallElement.typeOwnerKind)
                }
                else -> null
            }
        }
    }

    override fun resolveToClass(ktAnnotationEntry: KtAnnotationEntry, source: UElement): PsiClass? {
        analyzeForUast(ktAnnotationEntry) {
            val resolvedAnnotationCall = ktAnnotationEntry.resolveCall()?.singleCallOrNull<KtAnnotationCall>() ?: return null
            val resolvedAnnotationConstructorSymbol = resolvedAnnotationCall.symbol
            val ktType = resolvedAnnotationConstructorSymbol.returnType
            val context = containingKtClass(resolvedAnnotationConstructorSymbol) ?: ktAnnotationEntry
            return toPsiClass(ktType, source, context, ktAnnotationEntry.typeOwnerKind)
        }
    }

    override fun resolveToDeclaration(ktExpression: KtExpression): PsiElement? {
        if (ktExpression !is KtExpressionWithLabel && ktExpression !is KtCallExpression && ktExpression !is KtReferenceExpression) {
            return null
        }

        analyzeForUast(ktExpression) {
            val resolvedTargetSymbol = when (ktExpression) {
                is KtExpressionWithLabel -> {
                    ktExpression.getTargetLabel()?.mainReference?.resolveToSymbol()

                }

                is KtCallExpression -> {
                    resolveCall(ktExpression)?.let { return it }
                }

                is KtReferenceExpression -> {
                    ktExpression.mainReference.resolveToSymbol()
                }

                else -> null
            } ?: return null

            if (resolvedTargetSymbol is KtSyntheticJavaPropertySymbol && ktExpression is KtSimpleNameExpression) {
                // No PSI for this synthetic Java property. Either corresponding getter or setter has PSI.
                return resolveSyntheticJavaPropertyAccessorCall(ktExpression)
            }

            val project = ktExpression.project

            val resolvedTargetElement = psiForUast(resolvedTargetSymbol, project)


            // Shortcut: if the resolution target is compiled class/member, package info, or pure Java declarations,
            //   we can return it early here (to avoid expensive follow-up steps: module retrieval and light element conversion).
            if (resolvedTargetElement is ClsMemberImpl<*> ||
                resolvedTargetElement is PsiPackageImpl ||
                !isKotlin(resolvedTargetElement)
            ) {
                return resolvedTargetElement
            }

            if (resolvedTargetElement != null) {
                when (ProjectStructureProvider.getModule(project, resolvedTargetElement, null)) {
                    is KtSourceModule -> {
                        // `getMaybeLightElement` tries light element conversion first, and then something else for local declarations.
                        resolvedTargetElement.getMaybeLightElement(ktExpression)?.let { return it }
                    }

                    is KtLibraryModule -> {
                        // For decompiled declarations, we can try light element conversion (only).
                        (resolvedTargetElement as? KtDeclaration)?.toLightElements()?.singleOrNull()?.let { return it }
                    }

                    else -> {}
                }
            }

            fun resolveToPsiClassOrEnumEntry(classOrObject: KtClassOrObject): PsiElement? {
                val ktType = when (classOrObject) {
                    is KtEnumEntry -> {
                        classOrObject.getEnumEntrySymbol().callableIdIfNonLocal?.classId?.let(::buildClassType)
                    }

                    else -> {
                        // NB: Avoid symbol creation/retrieval
                        classOrObject.getClassId()?.let(::buildClassType)
                        // Fallback option for local class
                            ?: classOrObject.getClassOrObjectSymbol()?.let(::buildClassType)
                    }
                } ?: return null
                val psiClass = toPsiClass(ktType, source = null, classOrObject, classOrObject.typeOwnerKind)
                return when (classOrObject) {
                    is KtEnumEntry -> psiClass?.findFieldByName(classOrObject.name, false)
                    else -> psiClass
                }
            }

            if (resolvedTargetElement?.canBeAnalysed() == false) return null

            when (resolvedTargetElement) {
                is KtClassOrObject -> {
                    resolveToPsiClassOrEnumEntry(resolvedTargetElement)?.let { return it }
                }

                is KtConstructor<*> -> {
                    resolveToPsiClassOrEnumEntry(resolvedTargetElement.getContainingClassOrObject())?.let { return it }
                }

                is KtTypeAlias -> {
                    val ktType = resolvedTargetElement.getTypeAliasSymbol().expandedType
                    toPsiClass(
                        ktType,
                        source = null,
                        resolvedTargetElement,
                        resolvedTargetElement.typeOwnerKind
                    )?.let { return it }
                }

            is KtTypeParameter -> {
                    val ktType = buildTypeParameterType(resolvedTargetElement.getTypeParameterSymbol())
                    toPsiClass(
                        ktType,
                        ktExpression.toUElement(),
                        resolvedTargetElement,
                        resolvedTargetElement.typeOwnerKind
                    )?.let { return it }
                }

                is KtFunctionLiteral -> {
                    // Implicit lambda parameter `it`
                    if ((resolvedTargetSymbol as? KtValueParameterSymbol)?.isImplicitLambdaParameter == true) {
                        // From its containing lambda (of function literal), build ULambdaExpression
                        val lambda = resolvedTargetElement.toUElementOfType<ULambdaExpression>()
                        // and return javaPsi of the corresponding lambda implicit parameter
                        lambda?.valueParameters?.singleOrNull()?.javaPsi?.let { return it }
                    }
                }
            }

            // TODO: need to handle resolved target to library source
            return resolvedTargetElement
        }
    }

    override fun resolveToType(ktTypeReference: KtTypeReference, source: UElement, isBoxed: Boolean): PsiType? {
        analyzeForUast(ktTypeReference) {
            val ktType = ktTypeReference.getKtType()
            if (ktType is KtErrorType) return null
            return toPsiType(
                ktType,
                source,
                ktTypeReference,
                PsiTypeConversionConfiguration.create(ktTypeReference, isBoxed = isBoxed)
            )
        }
    }

    override fun resolveToType(ktTypeReference: KtTypeReference, containingLightDeclaration: PsiModifierListOwner?): PsiType? {
        analyzeForUast(ktTypeReference) {
            val ktType = ktTypeReference.getKtType()
            if (ktType is KtErrorType) return null
            return toPsiType(
                ktType,
                containingLightDeclaration,
                ktTypeReference,
                PsiTypeConversionConfiguration.create(ktTypeReference)
            )
        }
    }

    override fun getReceiverType(ktCallElement: KtCallElement, source: UElement): PsiType? {
        analyzeForUast(ktCallElement) {
            val ktCall = ktCallElement.resolveCall()?.singleFunctionCallOrNull() ?: return null
            return receiverType(ktCall, source, ktCallElement)
        }
    }

    override fun getAccessorReceiverType(ktSimpleNameExpression: KtSimpleNameExpression, source: UElement): PsiType? {
        analyzeForUast(ktSimpleNameExpression) {
            val ktCall = ktSimpleNameExpression.resolveCall()?.singleCallOrNull<KtVariableAccessCall>() ?: return null
            return receiverType(ktCall, source, ktSimpleNameExpression)
        }
    }

    override fun getDoubleColonReceiverType(ktDoubleColonExpression: KtDoubleColonExpression, source: UElement): PsiType? {
        analyzeForUast(ktDoubleColonExpression) {
            val receiverKtType = ktDoubleColonExpression.getReceiverKtType() ?: return null
            return toPsiType(
                receiverKtType,
                source,
                ktDoubleColonExpression,
                PsiTypeConversionConfiguration.create(ktDoubleColonExpression, isBoxed = true)
            )
        }
    }

    override fun getCommonSupertype(left: KtExpression, right: KtExpression, uExpression: UExpression): PsiType? {
        val ktElement = uExpression.sourcePsi as? KtExpression ?: return null
        analyzeForUast(ktElement) {
            val leftType = left.getKtType() ?: return null
            val rightType = right.getKtType()  ?: return null
            val commonSuperType = commonSuperType(listOf(leftType, rightType)) ?: return null
            return toPsiType(
                commonSuperType,
                uExpression,
                ktElement,
                PsiTypeConversionConfiguration.create(ktElement)
            )
        }
    }

    override fun getType(ktExpression: KtExpression, source: UElement): PsiType? {
        analyzeForUast(ktExpression) {
            val ktType = ktExpression.getKtType() ?: return null
            return toPsiType(
                ktType,
                source,
                ktExpression,
                PsiTypeConversionConfiguration.create(ktExpression)
            )
        }
    }

    override fun getType(ktDeclaration: KtDeclaration, source: UElement): PsiType? {
        analyzeForUast(ktDeclaration) {
            val ktType = ktDeclaration.getReturnKtType()
            return toPsiType(
                ktType,
                source,
                ktDeclaration,
                PsiTypeConversionConfiguration.create(
                    ktDeclaration,
                    isBoxed = ktType.isMarkedNullable,
                )
            )
        }
    }

    override fun getType(
        ktDeclaration: KtDeclaration,
        containingLightDeclaration: PsiModifierListOwner?,
        isForFake: Boolean,
    ): PsiType? {
        analyzeForUast(ktDeclaration) {
            val ktType = ktDeclaration.getReturnKtType()
            return toPsiType(
                ktType,
                containingLightDeclaration,
                ktDeclaration,
                PsiTypeConversionConfiguration.create(
                    ktDeclaration,
                    isBoxed = ktType.isMarkedNullable,
                    isForFake = isForFake,
                )
            )
        }
    }

    override fun getFunctionType(ktFunction: KtFunction, source: UElement?): PsiType? {
        if (ktFunction is KtConstructor<*>) return null
        analyzeForUast(ktFunction) {
            return toPsiType(ktFunction.getFunctionalType(), source, ktFunction, PsiTypeConversionConfiguration.create(ktFunction))
        }
    }

    override fun getFunctionalInterfaceType(uLambdaExpression: KotlinULambdaExpression): PsiType? {
        val sourcePsi = uLambdaExpression.sourcePsi
        analyzeForUast(sourcePsi) {
            val samType = sourcePsi.getExpectedType()
                ?.takeIf { it !is KtErrorType && it.isFunctionalInterfaceType }
                ?.lowerBoundIfFlexible()
                ?: return null
            return toPsiType(samType, uLambdaExpression, sourcePsi, PsiTypeConversionConfiguration.create(sourcePsi))
        }
    }

    override fun hasInheritedGenericType(psiElement: PsiElement): Boolean {
        return when (psiElement) {
            is KtTypeReference ->
                analyzeForUast(psiElement) {
                    isInheritedGenericType(psiElement.getKtType())
                }
            is KtCallableDeclaration ->
                analyzeForUast(psiElement) {
                    isInheritedGenericType(getKtType(psiElement))
                }
            is KtDestructuringDeclaration ->
                analyzeForUast(psiElement) {
                    isInheritedGenericType(psiElement.getReturnKtType())
                }
            else -> false
        }
    }

    override fun nullability(psiElement: PsiElement): KtTypeNullability? {
        return when (psiElement) {
            is KtTypeReference ->
                analyzeForUast(psiElement) {
                    nullability(psiElement.getKtType())
                }
            is KtCallableDeclaration ->
                analyzeForUast(psiElement) {
                    nullability(getKtType(psiElement))
                }
            is KtDestructuringDeclaration ->
                analyzeForUast(psiElement) {
                    nullability(psiElement.getReturnKtType())
                }
            else -> null
        }
    }

    override fun evaluate(uExpression: UExpression): Any? {
        val ktExpression = uExpression.sourcePsi as? KtExpression ?: return null
        analyzeForUast(ktExpression) {
            return ktExpression.evaluate(KtConstantEvaluationMode.CONSTANT_LIKE_EXPRESSION_EVALUATION)
                ?.takeUnless { it is KtConstantValue.KtErrorConstantValue }?.value
        }
    }
}
