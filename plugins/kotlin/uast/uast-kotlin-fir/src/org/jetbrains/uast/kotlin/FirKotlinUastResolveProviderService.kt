// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.uast.kotlin

import com.intellij.psi.*
import com.intellij.psi.impl.compiled.ClsMemberImpl
import com.intellij.psi.impl.file.PsiPackageImpl
import com.intellij.util.SmartList
import com.intellij.util.containers.addIfNotNull
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.projectStructure.KaLibraryModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModuleProvider
import org.jetbrains.kotlin.analysis.api.projectStructure.KaSourceModule
import org.jetbrains.kotlin.analysis.api.resolution.*
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaNamedSymbol
import org.jetbrains.kotlin.analysis.api.types.KaErrorType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeMappingMode
import org.jetbrains.kotlin.analysis.api.types.KaTypeNullability
import org.jetbrains.kotlin.asJava.toLightAnnotation
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.name.StandardClassIds
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

    fun isSupportedFile(file: KtFile): Boolean = true

    override fun convertToPsiAnnotation(ktElement: KtElement): PsiAnnotation? {
        return ktElement.toLightAnnotation()
    }

    override fun convertValueArguments(ktCallElement: KtCallElement, parent: UElement): List<UNamedExpression>? {
        analyzeForUast(ktCallElement) {
            val argumentMapping = ktCallElement.resolveToCall()?.singleFunctionCallOrNull()?.argumentMapping ?: return null
            val handledParameters = mutableSetOf<KaValueParameterSymbol>()
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
            val resolvedAnnotationCall = annotationEntry.resolveToCall()?.singleCallOrNull<KaAnnotationCall>() ?: return null
            val parameter = resolvedAnnotationCall.argumentMapping[arg.getArgumentExpression()]?.symbol ?: return null
            val namedExpression = uAnnotation.attributeValues.find { it.name == parameter.name.asString() }
            return namedExpression?.expression as? KotlinUVarargExpression ?: namedExpression
        }
    }

    override fun findDefaultValueForAnnotationAttribute(ktCallElement: KtCallElement, name: String): UExpression? {
        analyzeForUast(ktCallElement) {
            val resolvedAnnotationConstructorSymbol =
                ktCallElement.resolveToCall()?.singleConstructorCallOrNull()?.symbol ?: return null
            val psi = resolvedAnnotationConstructorSymbol.psi
            if (psi is PsiClass) {
                // a usage Java annotation
                return findAttributeValueExpression(psi, name)
            }
            val parameter = resolvedAnnotationConstructorSymbol.valueParameters.find { it.name.asString() == name } ?: return null
            return (parameter.psi as? KtParameter)?.defaultValue?.let(languagePlugin::convertWithParent)
        }
    }

    override fun getArgumentForParameter(ktCallElement: KtCallElement, index: Int, parent: UElement): UExpression? {
        analyzeForUast(ktCallElement) {
            val resolvedFunctionCall = ktCallElement.resolveToCall()?.singleFunctionCallOrNull()
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

    @OptIn(KaExperimentalApi::class)
    override fun getImplicitParameters(
        ktLambdaExpression: KtLambdaExpression,
        parent: UElement,
        includeExplicitParameters: Boolean
    ): List<KotlinUParameter> {
        analyzeForUast(ktLambdaExpression) {
            val anonymousFunctionSymbol = ktLambdaExpression.functionLiteral.symbol
            val parameters = mutableListOf<KotlinUParameter>()
            if (includeExplicitParameters && anonymousFunctionSymbol.receiverParameter != null) {
                val lambdaImplicitReceiverType = anonymousFunctionSymbol.receiverParameter!!.type.asPsiType(
                    ktLambdaExpression,
                    allowErrorTypes = false,
                    KaTypeMappingMode.DEFAULT_UAST,
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
                    KaTypeMappingMode.DEFAULT_UAST,
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
        val candidates = analyzeForUast(ktExpression) {
            buildList {
                ktExpression.resolveToCallCandidates().forEach { candidateInfo ->
                    when (val candidate = candidateInfo.candidate) {
                        is KaFunctionCall<*> -> {
                            add(candidate.partiallyAppliedSymbol.symbol)
                        }

                        is KaCompoundVariableAccessCall -> {
                            val variableSymbol = candidate.variablePartiallyAppliedSymbol.symbol
                            if (variableSymbol is KaSyntheticJavaPropertySymbol) {
                                add(variableSymbol.getter)
                                addIfNotNull(variableSymbol.setter)
                            } else {
                                add(variableSymbol)
                            }
                            add(candidate.compoundOperation.operationPartiallyAppliedSymbol.symbol)
                        }

                        is KaCompoundArrayAccessCall -> {
                            add(candidate.getPartiallyAppliedSymbol.symbol)
                            add(candidate.setPartiallyAppliedSymbol.symbol)
                            add(candidate.compoundOperation.operationPartiallyAppliedSymbol.symbol)
                        }

                        else -> {}
                    }
                }
            }.map { it.createPointer() }
        }
        if (candidates.isEmpty()) return emptySequence()
        return sequence {
            candidates.forEach { candidatePointer ->
                analyzeForUast(ktExpression) {
                    val candidate = candidatePointer.restoreSymbol() ?: return@forEach
                    val psi = when (candidate) {
                        is KaVariableSymbol -> psiForUast(candidate)
                        is KaFunctionSymbol -> toPsiMethod(candidate, ktExpression)
                    }?: return@forEach
                    yield(psi)
                }
            }
        }
    }

    override fun resolveBitwiseOperators(ktBinaryExpression: KtBinaryExpression): UastBinaryOperator {
        val other = UastBinaryOperator.OTHER
        analyzeForUast(ktBinaryExpression) {
            val resolvedCall = ktBinaryExpression.resolveToCall()?.singleFunctionCallOrNull() ?: return other
            val operatorName = resolvedCall.symbol.callableId?.callableName?.asString() ?: return other
            return KotlinUBinaryExpression.BITWISE_OPERATORS[operatorName] ?: other
        }
    }

    override fun resolveCall(ktElement: KtElement): PsiMethod? {
        analyzeForUast(ktElement) {
            val kaCallInfo = ktElement.resolveToCall() ?: return null
            kaCallInfo.singleFunctionCallOrNull()
                ?.symbol
                ?.let { return toPsiMethod(it, ktElement, kaCallInfo) }
            return when (ktElement) {
                is KtBinaryExpression,
                is KtPrefixExpression,
                is KtPostfixExpression -> {
                    kaCallInfo.singleCallOrNull<KaCompoundVariableAccessCall>()
                        ?.compoundOperation
                        ?.operationPartiallyAppliedSymbol
                        ?.signature
                        ?.symbol
                        ?.let { toPsiMethod(it, ktElement, kaCallInfo) }
                }

                else -> null
            }
        }
    }

    override fun resolveSyntheticJavaPropertyAccessorCall(ktSimpleNameExpression: KtSimpleNameExpression): PsiMethod? {
        return analyzeForUast(ktSimpleNameExpression) {
            val kaCallInfo = ktSimpleNameExpression.resolveToCall() ?: return null
            val variableAccessCall = kaCallInfo.singleCallOrNull<KaSimpleVariableAccessCall>() ?: return null
            val propertySymbol = variableAccessCall.symbol as? KaSyntheticJavaPropertySymbol ?: return null
            when (variableAccessCall.simpleAccess) {
                is KaSimpleVariableAccess.Read -> {
                    toPsiMethod(propertySymbol.javaGetterSymbol, ktSimpleNameExpression, kaCallInfo)
                }
                is KaSimpleVariableAccess.Write -> {
                    toPsiMethod(propertySymbol.javaSetterSymbol?: return null, ktSimpleNameExpression, kaCallInfo)
                }
            }
        }
    }

    override fun isResolvedToExtension(ktCallElement: KtCallElement): Boolean {
        analyzeForUast(ktCallElement) {
            val ktCall = ktCallElement.resolveToCall()?.singleFunctionCallOrNull() ?: return false
            return ktCall.symbol.isExtension
        }
    }

    override fun resolvedFunctionName(ktCallElement: KtCallElement): String? {
        analyzeForUast(ktCallElement) {
            val resolvedFunctionLikeSymbol = ktCallElement.resolveToCall()?.singleFunctionCallOrNull()?.symbol ?: return null
            return (resolvedFunctionLikeSymbol as? KaNamedSymbol)?.name?.identifierOrNullIfSpecial
                ?: (resolvedFunctionLikeSymbol as? KaConstructorSymbol)?.let { SpecialNames.INIT.asString() }
        }
    }

    override fun qualifiedAnnotationName(ktCallElement: KtCallElement): String? {
        analyzeForUast(ktCallElement) {
            val resolvedAnnotationConstructorSymbol =
                ktCallElement.resolveToCall()?.singleConstructorCallOrNull()?.symbol ?: return null
            return resolvedAnnotationConstructorSymbol.containingClassId
                ?.asSingleFqName()
                ?.toString()
        }
    }

    override fun callKind(ktCallElement: KtCallElement): UastCallKind {
        analyzeForUast(ktCallElement) {
            val resolvedFunctionLikeSymbol =
                ktCallElement.resolveToCall()?.singleFunctionCallOrNull()?.symbol ?: return UastCallKind.METHOD_CALL
            val fqName = resolvedFunctionLikeSymbol.callableId?.asSingleFqName()
            return when {
                resolvedFunctionLikeSymbol is KaSamConstructorSymbol ||
                        resolvedFunctionLikeSymbol is KaConstructorSymbol -> UastCallKind.CONSTRUCTOR_CALL

                fqName != null && isAnnotationArgumentArrayInitializer(ktCallElement, fqName) -> UastCallKind.NESTED_ARRAY_INITIALIZER
                else -> UastCallKind.METHOD_CALL
            }
        }
    }

    override fun isAnnotationConstructorCall(ktCallElement: KtCallElement): Boolean {
        analyzeForUast(ktCallElement) {
            val resolvedAnnotationConstructorSymbol =
                ktCallElement.resolveToCall()?.singleConstructorCallOrNull()?.symbol ?: return false
            val ktType = resolvedAnnotationConstructorSymbol.returnType
            val context = containingKtClass(resolvedAnnotationConstructorSymbol) ?: ktCallElement
            val psiClass = toPsiClass(ktType, null, context, ktCallElement.typeOwnerKind) ?: return false
            return psiClass.isAnnotationType
        }
    }

    override fun resolveToClassIfConstructorCall(ktCallElement: KtCallElement, source: UElement): PsiClass? {
        analyzeForUast(ktCallElement) {
            val resolvedFunctionLikeSymbol = ktCallElement.resolveToCall()?.singleFunctionCallOrNull()?.symbol ?: return null
            return when (resolvedFunctionLikeSymbol) {
                is KaConstructorSymbol -> {
                    val context = containingKtClass(resolvedFunctionLikeSymbol) ?: ktCallElement
                    toPsiClass(resolvedFunctionLikeSymbol.returnType, source, context, ktCallElement.typeOwnerKind)
                }

                is KaSamConstructorSymbol -> {
                    toPsiClass(resolvedFunctionLikeSymbol.returnType, source, ktCallElement, ktCallElement.typeOwnerKind)
                }

                else -> null
            }
        }
    }

    override fun resolveToClass(ktAnnotationEntry: KtAnnotationEntry, source: UElement): PsiClass? {
        analyzeForUast(ktAnnotationEntry) {
            val resolvedAnnotationCall = ktAnnotationEntry.resolveToCall()?.singleCallOrNull<KaAnnotationCall>() ?: return null
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
                is KtInstanceExpressionWithLabel -> {
                    // A subtype of [KtExpressionWithLabel], including [KtThisExpression]/[KtSuperExpression]
                    // Therefore, it must be handled *before* that super type.
                    val reference =
                        // `this@withLabel`
                        ktExpression.getTargetLabel()?.mainReference
                            // just `this` without label
                            ?: ktExpression.instanceReference.mainReference
                    reference.resolveToSymbol()
                }
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

            if (resolvedTargetSymbol is KaSyntheticJavaPropertySymbol && ktExpression is KtSimpleNameExpression) {
                // No PSI for this synthetic Java property. Either corresponding getter or setter has PSI.
                return resolveSyntheticJavaPropertyAccessorCall(ktExpression)
            }

            val project = ktExpression.project

            val resolvedTargetElement =
                when (resolvedTargetSymbol) {
                    is KaBackingFieldSymbol -> {
                        // [KtBackingFieldSymbol] itself has `null` psi, and thus going to
                        // the below default logic, [psiForUast], will return `null` too.
                        // Use the owning property's psi and let [getMaybeLightElement] find
                        // the corresponding [PsiField] for the backing field.
                        resolvedTargetSymbol.owningProperty.psi
                    }
                    else -> {
                        psiForUast(resolvedTargetSymbol)
                    }
                }

            // Shortcut: if the resolution target is compiled class/member, package info, or pure Java declarations,
            //   we can return it early here (to avoid expensive follow-up steps: module retrieval and light element conversion).
            if (resolvedTargetElement is ClsMemberImpl<*> ||
                resolvedTargetElement is PsiPackageImpl ||
                !isKotlin(resolvedTargetElement)
            ) {
                return resolvedTargetElement
            }

            if (resolvedTargetElement != null) {
                when (KaModuleProvider.getModule(project, resolvedTargetElement, useSiteModule = null)) {
                    is KaSourceModule -> {
                        // `getMaybeLightElement` tries light element conversion first, and then something else for local declarations.
                        resolvedTargetElement.getMaybeLightElement(ktExpression)?.let { return it }
                    }

                    is KaLibraryModule -> {
                        // For decompiled declarations, we can try light element conversion (only).
                        (resolvedTargetElement as? KtElement)?.toPsiElementAsLightElement(ktExpression)?.let { return it }
                    }

                    else -> {}
                }
            }

            fun resolveToPsiClassOrEnumEntry(classOrObject: KtClassOrObject): PsiElement? {
                val ktType = when (classOrObject) {
                    is KtEnumEntry -> {
                        classOrObject.symbol.callableId?.classId?.let(::buildClassType)
                    }
                    else -> {
                        // NB: Avoid symbol creation/retrieval
                        classOrObject.getClassId()?.let(::buildClassType)
                        // Fallback option for local class
                            ?: classOrObject.classSymbol?.let(::buildClassType)
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
                    val ktType = resolvedTargetElement.symbol.expandedType
                    toPsiClass(
                        ktType,
                        source = null,
                        resolvedTargetElement,
                        resolvedTargetElement.typeOwnerKind
                    )?.let { return it }
                }

                is KtTypeParameter -> {
                    val ktType = buildTypeParameterType(resolvedTargetElement.symbol)
                    toPsiClass(
                        ktType,
                        ktExpression.toUElement(),
                        resolvedTargetElement,
                        resolvedTargetElement.typeOwnerKind
                    )?.let { return it }
                }

                is KtTypeReference -> {
                    if (resolvedTargetSymbol is KaReceiverParameterSymbol) {
                        // Explicit `this` resolved to type reference if it belongs to an extension callable
                        when (val callable = resolvedTargetSymbol.owningCallableSymbol) {
                            is KaFunctionSymbol -> {
                                val psiMethod = toPsiMethod(callable, ktExpression)
                                psiMethod?.parameterList?.parameters?.firstOrNull()?.let { return it }
                            }
                            is KaPropertySymbol -> {
                                val getter = callable.getter?.let { toPsiMethod(it, ktExpression) }
                                getter?.parameterList?.parameters?.firstOrNull()?.let { return it }
                            }
                            else -> {}
                        }
                    } else {
                        val ktType = resolvedTargetElement.type
                        toPsiClass(
                            ktType,
                            ktExpression.toUElement(),
                            resolvedTargetElement,
                            resolvedTargetElement.typeOwnerKind
                        )?.let { return it }
                    }
                }

                is KtFunctionLiteral -> {
                    // Implicit lambda parameter `it`
                    if ((resolvedTargetSymbol as? KaValueParameterSymbol)?.isImplicitLambdaParameter == true) {
                        // From its containing lambda (of function literal), build ULambdaExpression
                        val lambda = resolvedTargetElement.toUElementOfType<ULambdaExpression>()
                        // and return javaPsi of the corresponding lambda implicit parameter
                        lambda?.valueParameters?.singleOrNull()?.javaPsi?.let { return it }
                    }
                    val isLambdaReceiver =
                        // Implicit `this` as the lambda receiver
                        resolvedTargetSymbol is KaAnonymousFunctionSymbol ||
                                // Explicit `this`
                                resolvedTargetSymbol is KaReceiverParameterSymbol
                    if (isLambdaReceiver) {
                        // From its containing lambda (of function literal), build ULambdaExpression
                        val lambda = resolvedTargetElement.toUElementOfType<ULambdaExpression>()
                        // and return javaPsi of the corresponding lambda receiver
                        lambda?.parameters?.firstOrNull()?.javaPsi?.let { return it }
                    }
                }
            }

            // TODO: need to handle resolved target to library source
            return resolvedTargetElement
        }
    }

    override fun resolveToType(ktTypeReference: KtTypeReference, source: UElement, isBoxed: Boolean): PsiType? {
        analyzeForUast(ktTypeReference) {
            val ktType = ktTypeReference.type
            if (ktType is KaErrorType) return null
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
            val ktType = ktTypeReference.type
            if (ktType is KaErrorType) return null
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
            val ktCall = ktCallElement.resolveToCall()?.singleFunctionCallOrNull() ?: return null
            return receiverType(ktCall, source, ktCallElement)
        }
    }

    override fun getAccessorReceiverType(ktSimpleNameExpression: KtSimpleNameExpression, source: UElement): PsiType? {
        analyzeForUast(ktSimpleNameExpression) {
            val ktCall = ktSimpleNameExpression.resolveToCall()?.singleCallOrNull<KaVariableAccessCall>() ?: return null
            return receiverType(ktCall, source, ktSimpleNameExpression)
        }
    }

    override fun getDoubleColonReceiverType(ktDoubleColonExpression: KtDoubleColonExpression, source: UElement): PsiType? {
        analyzeForUast(ktDoubleColonExpression) {
            val receiverKtType = ktDoubleColonExpression.receiverType ?: return null
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
            val leftType = left.expressionType ?: return null
            val rightType = right.expressionType ?: return null
            val commonSuperType = listOf(leftType, rightType).commonSupertype
            return toPsiType(
                commonSuperType,
                uExpression,
                ktElement,
                PsiTypeConversionConfiguration.create(ktElement)
            )
        }
    }

    override fun getType(ktExpression: KtExpression, source: UElement): PsiType? {
        // Analysis API returns [Unit] for statements, but [FirStatement] is too broad,
        // e.g., that even includes [FirFunctionCall], which definitely has an expression type.
        // We can't use [KtPsiUtil.isStatement]: https://kotlinlang.org/spec/statements.html
        // which, again, is too broad as well, e.g., code block, which might be a lambda body
        // whose return type (if it's [Unit]) might be meaningful (coercion-to-Unit).
        // Here we bail out syntactical statements that are very obvious.
        if (ktExpression is KtLoopExpression || ktExpression is KtWhenEntry)
            return null
        // Obvious constructor calls
        val parent = ktExpression.parent
        // E.g. class Sub : Base(...)
        if (ktExpression is KtSuperTypeCallEntry || parent is KtSuperTypeCallEntry)
            return null
        // E.g. constructor(...) : this(...)
        if (ktExpression is KtConstructorDelegationCall || parent is KtConstructorDelegationCall)
            return null

        analyzeForUast(ktExpression) {
            val ktType = ktExpression.expressionType ?: return null
            // Again, Analysis API returns [Unit] for statements, so we need to filter out
            // some cases that are not actually expression's return type.
            if (ktType.isUnitType) {
                // E.g., AnnotationTarget.FIELD, reference to enum class is resolved to the constructor call,
                // and then returned as Unit expression type. Same for path segments in a fully qualified name
                if ((parent is KtQualifiedExpression || parent is KtDoubleColonExpression) &&
                    (ktExpression is KtQualifiedExpression || ktExpression is KtNameReferenceExpression)
                ) {
                    return null
                }
            }
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
            val ktType = ktDeclaration.returnType
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
            val ktType = ktDeclaration.returnType
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

    override fun hasTypeForValueClassInSignature(ktDeclaration: KtDeclaration): Boolean {
        analyzeForUast(ktDeclaration) {
            val symbol = ktDeclaration.symbol as? KaCallableSymbol ?: return false
            if (symbol.returnType.typeForValueClass) return true
            if (symbol.receiverType?.typeForValueClass == true) return true
            if (symbol is KaFunctionSymbol) {
                return symbol.valueParameters.any { it.returnType.typeForValueClass }
            }
            return false
        }
    }

    override fun getSuspendContinuationType(
        suspendFunction: KtFunction,
        containingLightDeclaration: PsiModifierListOwner?,
    ): PsiType? {
        analyzeForUast(suspendFunction) {
            val symbol = suspendFunction.symbol as? KaNamedFunctionSymbol ?: return null
            if (!symbol.isSuspend) return null
            val continuationType = buildClassType(StandardClassIds.Continuation) { argument(symbol.returnType) }
            return toPsiType(
                continuationType,
                containingLightDeclaration,
                suspendFunction,
                PsiTypeConversionConfiguration.create(suspendFunction)
            )
        }
    }

    override fun getFunctionType(ktFunction: KtFunction, source: UElement?): PsiType? {
        if (ktFunction is KtConstructor<*>) return null
        analyzeForUast(ktFunction) {
            return toPsiType(ktFunction.functionType, source, ktFunction, PsiTypeConversionConfiguration.create(ktFunction))
        }
    }

    override fun getFunctionalInterfaceType(uLambdaExpression: KotlinULambdaExpression): PsiType? {
        val sourcePsi = uLambdaExpression.sourcePsi
        analyzeForUast(sourcePsi) {
            val samType = sourcePsi.expectedType
                ?.takeIf { it !is KaErrorType && it.isFunctionalInterface }
                ?.lowerBoundIfFlexible()
                ?: return null
            return toPsiType(samType, uLambdaExpression, sourcePsi, PsiTypeConversionConfiguration.create(sourcePsi))
        }
    }

    override fun hasInheritedGenericType(psiElement: PsiElement): Boolean {
        return getTargetType(psiElement, false) { ktType ->
            isInheritedGenericType(ktType)
        }
    }

    override fun nullability(psiElement: PsiElement): KaTypeNullability? {
        return getTargetType(psiElement, null) { ktType ->
            nullability(ktType)
        }
    }

    override fun modality(ktDeclaration: KtDeclaration): Modality? = analyzeForUast(ktDeclaration) {
        when (ktDeclaration.symbol.modality) {
            KaSymbolModality.FINAL -> Modality.FINAL
            KaSymbolModality.SEALED -> Modality.SEALED
            KaSymbolModality.OPEN -> Modality.OPEN
            KaSymbolModality.ABSTRACT -> Modality.ABSTRACT
        }
    }

    private inline fun <R> getTargetType(
        psiElement: PsiElement,
        defaultValue: R,
        typeConsumer: KaSession.(KaType?) -> R,
    ): R {
        return when (psiElement) {
            is KtTypeReference -> {
                analyzeForUast(psiElement) {
                    typeConsumer(psiElement.type)
                }
            }

            is KtCallableDeclaration -> {
                // NB: We should not use [KtDeclaration.getReturnKtType]; see its comment:
                // IMPORTANT: For `vararg foo: T` parameter returns full `Array<out T>` type
                // (unlike [KaValueParameterSymbol.returnType] which returns `T`).
                analyzeForUast(psiElement) {
                    typeConsumer(getKtType(psiElement))
                }
            }

            is KtPropertyAccessor -> {
                // Not necessary to use the containing property
                // getter: its return type should be the same as property
                // setter: it's always `void`, and its (implicit) setter parameter is callable.
                analyzeForUast(psiElement) {
                    typeConsumer(psiElement.returnType)
                }
            }

            is KtDestructuringDeclaration -> {
                analyzeForUast(psiElement) {
                    typeConsumer(psiElement.returnType)
                }
            }

            else -> defaultValue
        }
    }

    override fun evaluate(uExpression: UExpression): Any? {
        return FirKotlinUastConstantEvaluator.evaluate(uExpression)
    }
}
