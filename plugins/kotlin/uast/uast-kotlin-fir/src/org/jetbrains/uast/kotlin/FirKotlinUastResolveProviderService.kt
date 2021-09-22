// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import com.intellij.psi.*
import com.intellij.util.SmartList
import org.jetbrains.kotlin.analysis.api.KtTypeArgumentWithVariance
import org.jetbrains.kotlin.analysis.api.analyseForUast
import org.jetbrains.kotlin.analysis.api.calls.KtAnnotationCall
import org.jetbrains.kotlin.analysis.api.symbols.KtConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtSamConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtValueParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtNamedSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtLiteralConstantValue
import org.jetbrains.kotlin.analysis.api.types.*
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.types.typeUtil.TypeNullability
import org.jetbrains.uast.*
import org.jetbrains.uast.kotlin.internal.*
import org.jetbrains.uast.kotlin.psi.UastKotlinPsiParameterBase

interface FirKotlinUastResolveProviderService : BaseKotlinUastResolveProviderService {

    override val languagePlugin: UastLanguagePlugin
        get() = firKotlinUastPlugin

    override val baseKotlinConverter: BaseKotlinConverter
        get() = FirKotlinConverter

    private val KtExpression.parentValueArgument: ValueArgument?
        get() = parents.firstOrNull { it is ValueArgument } as? ValueArgument

    override fun convertValueArguments(ktCallElement: KtCallElement, parent: UElement): List<UNamedExpression>? {
        analyseForUast(ktCallElement) {
            val argumentMapping = ktCallElement.resolveCall()?.argumentMapping ?: return null
            val handledParameters = mutableSetOf<KtValueParameterSymbol>()
            val valueArguments = SmartList<UNamedExpression>()
            // NB: we need a loop over call element's value arguments to preserve their order.
            ktCallElement.valueArguments.forEach {
                val parameter = argumentMapping[it.getArgumentExpression()] ?: return@forEach
                if (!handledParameters.add(parameter)) return@forEach
                val arguments = argumentMapping.entries
                    .filter { (_, param) -> param == parameter }
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
        analyseForUast(annotationEntry) {
            val resolvedAnnotationCall = annotationEntry.resolveCall() as? KtAnnotationCall ?: return null
            val parameter = resolvedAnnotationCall.argumentMapping[arg.getArgumentExpression()] ?: return null
            val namedExpression = uAnnotation.attributeValues.find { it.name == parameter.name.asString() }
            return namedExpression?.expression as? KotlinUVarargExpression ?: namedExpression
        }
    }

    override fun findDefaultValueForAnnotationAttribute(ktCallElement: KtCallElement, name: String): KtExpression? {
        analyseForUast(ktCallElement) {
            val resolvedAnnotationConstructorSymbol =
                ktCallElement.resolveCall()?.targetFunction?.candidates?.singleOrNull() as? KtConstructorSymbol ?: return null
            val parameter = resolvedAnnotationConstructorSymbol.valueParameters.find { it.name.asString() == name } ?: return null
            return (parameter.psi as? KtParameter)?.defaultValue
        }
    }

    override fun getArgumentForParameter(ktCallElement: KtCallElement, index: Int, parent: UElement): UExpression? {
        analyseForUast(ktCallElement) {
            val resolvedCall = ktCallElement.resolveCall() ?: return null
            val resolvedFunctionLikeSymbol = resolvedCall.targetFunction.candidates.singleOrNull() ?: return null
            val parameter = resolvedFunctionLikeSymbol.valueParameters[index]
            val arguments = resolvedCall.argumentMapping.entries
                .filter { (_, param) -> param == parameter }
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
        analyseForUast(ktLambdaExpression) {
            // TODO: Should check an explicit, expected return type as well
            //  e.g., val y: () -> Unit = { 1 } // the lambda return type is Int, but we won't add an implicit return here.
            val returnType = ktLambdaExpression.functionLiteral.getAnonymousFunctionSymbol().annotatedType.type
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
        // TODO receiver parameter, dispatch parameter like in org.jetbrains.uast.kotlin.KotlinUastResolveProviderService.getImplicitParameters
        analyseForUast(ktLambdaExpression) {
            return ktLambdaExpression.functionLiteral.getAnonymousFunctionSymbol().valueParameters.map { p ->
                KotlinUParameter(
                    UastKotlinPsiParameterBase(
                        name = p.name.asString(),
                        // TODO: implicit parameter type
                        type = UastErrorType,
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
        }
    }

    override fun resolveBitwiseOperators(ktBinaryExpression: KtBinaryExpression): UastBinaryOperator {
        val other = UastBinaryOperator.OTHER
        val resolvedOperator = resolveCall(ktBinaryExpression) ?: return other
        return KotlinUBinaryExpression.BITWISE_OPERATORS[resolvedOperator.name] ?: other
    }

    override fun resolveCall(ktElement: KtElement): PsiMethod? {
        when (ktElement) {
            is KtCallElement -> {
                analyseForUast(ktElement) {
                    return ktElement.resolveCall()?.let { toPsiMethod(it) }
                }
            }
            is KtBinaryExpression -> {
                analyseForUast(ktElement) {
                    return ktElement.resolveCall()?.let { toPsiMethod(it) }
                }
            }
            is KtUnaryExpression -> {
                analyseForUast(ktElement) {
                    return ktElement.resolveCall()?.let { toPsiMethod(it) }
                }
            }
            else ->
                return null
        }
    }

    override fun resolveAccessorCall(ktSimpleNameExpression: KtSimpleNameExpression): PsiMethod? {
        analyseForUast(ktSimpleNameExpression) {
            return ktSimpleNameExpression.resolveAccessorCall()?.let { toPsiMethod(it) }
        }
    }

    override fun isResolvedToExtension(ktCallElement: KtCallElement): Boolean {
        analyseForUast(ktCallElement) {
            val resolvedFunctionLikeSymbol = ktCallElement.resolveCall()?.targetFunction?.candidates?.singleOrNull() ?: return false
            return resolvedFunctionLikeSymbol.isExtension
        }
    }

    override fun resolvedFunctionName(ktCallElement: KtCallElement): String? {
        analyseForUast(ktCallElement) {
            val resolvedFunctionLikeSymbol = ktCallElement.resolveCall()?.targetFunction?.candidates?.singleOrNull() ?: return null
            return (resolvedFunctionLikeSymbol as? KtNamedSymbol)?.name?.identifierOrNullIfSpecial
                ?: (resolvedFunctionLikeSymbol as? KtConstructorSymbol)?.let { SpecialNames.INIT.asString() }
        }
    }

    override fun qualifiedAnnotationName(ktCallElement: KtCallElement): String? {
        analyseForUast(ktCallElement) {
            val resolvedAnnotationConstructorSymbol =
                ktCallElement.resolveCall()?.targetFunction?.candidates?.singleOrNull() as? KtConstructorSymbol ?: return null
            return resolvedAnnotationConstructorSymbol.containingClassIdIfNonLocal
                ?.asSingleFqName()
                ?.toString()
        }
    }

    override fun callKind(ktCallElement: KtCallElement): UastCallKind {
        analyseForUast(ktCallElement) {
            val resolvedFunctionLikeSymbol =
                ktCallElement.resolveCall()?.targetFunction?.candidates?.singleOrNull() ?: return UastCallKind.METHOD_CALL
            val fqName = resolvedFunctionLikeSymbol.callableIdIfNonLocal?.asSingleFqName()
            return when {
                resolvedFunctionLikeSymbol is KtConstructorSymbol -> UastCallKind.CONSTRUCTOR_CALL
                fqName != null && isAnnotationArgumentArrayInitializer(ktCallElement, fqName) -> UastCallKind.NESTED_ARRAY_INITIALIZER
                else -> UastCallKind.METHOD_CALL
            }
        }
    }

    override fun isAnnotationConstructorCall(ktCallElement: KtCallElement): Boolean {
        analyseForUast(ktCallElement) {
            val resolvedAnnotationConstructorSymbol =
                ktCallElement.resolveCall()?.targetFunction?.candidates?.singleOrNull() as? KtConstructorSymbol ?: return false
            val ktType = resolvedAnnotationConstructorSymbol.annotatedType.type
            val psiClass = toPsiClass(ktType, null, ktCallElement, ktCallElement.typeOwnerKind) ?: return false
            return psiClass.isAnnotationType
        }
    }

    override fun resolveToClassIfConstructorCall(ktCallElement: KtCallElement, source: UElement): PsiClass? {
        analyseForUast(ktCallElement) {
            val resolvedFunctionLikeSymbol = ktCallElement.resolveCall()?.targetFunction?.candidates?.singleOrNull() ?: return null
            return when (resolvedFunctionLikeSymbol) {
                is KtConstructorSymbol,
                is KtSamConstructorSymbol -> {
                    toPsiClass(resolvedFunctionLikeSymbol.annotatedType.type, source, ktCallElement, ktCallElement.typeOwnerKind)
                }
                else -> null
            }
        }
    }

    override fun resolveToClass(ktAnnotationEntry: KtAnnotationEntry, source: UElement): PsiClass? {
        analyseForUast(ktAnnotationEntry) {
            val resolvedAnnotationCall = ktAnnotationEntry.resolveCall() as? KtAnnotationCall ?: return null
            val resolvedAnnotationConstructorSymbol =
                resolvedAnnotationCall.targetFunction.candidates.singleOrNull() as? KtConstructorSymbol ?: return null
            val ktType = resolvedAnnotationConstructorSymbol.annotatedType.type
            return toPsiClass(ktType, source, ktAnnotationEntry, ktAnnotationEntry.typeOwnerKind)
        }
    }

    override fun resolveToDeclaration(ktExpression: KtExpression): PsiElement? {
        when (ktExpression) {
            is KtExpressionWithLabel -> {
                analyseForUast(ktExpression) {
                    return ktExpression.getTargetLabel()?.mainReference?.resolve()
                }
            }
            is KtReferenceExpression -> {
                analyseForUast(ktExpression) {
                    return ktExpression.mainReference.resolve()
                }
            }
            else ->
                return null
        }
    }

    override fun resolveToType(ktTypeReference: KtTypeReference, source: UElement, boxed: Boolean): PsiType? {
        analyseForUast(ktTypeReference) {
            val ktType = ktTypeReference.getKtType()
            if (ktType is KtClassErrorType) return null
            return toPsiType(ktType, source, ktTypeReference, ktTypeReference.typeOwnerKind, boxed)
        }
    }

    override fun resolveToType(ktTypeReference: KtTypeReference, lightDeclaration: PsiModifierListOwner?): PsiType? {
        analyseForUast(ktTypeReference) {
            val ktType = ktTypeReference.getKtType()
            if (ktType is KtClassErrorType) return null
            return toPsiType(ktType, lightDeclaration, ktTypeReference, ktTypeReference.typeOwnerKind)
        }
    }

    override fun getReceiverType(ktCallElement: KtCallElement, source: UElement): PsiType? {
        analyseForUast(ktCallElement) {
            val ktType = ktCallElement.resolveCall()?.targetFunction?.candidates?.singleOrNull()?.receiverType?.type ?: return null
            if (ktType is KtClassErrorType) return null
            return toPsiType(ktType, source, ktCallElement, ktCallElement.typeOwnerKind, boxed = true)
        }
    }

    override fun getAccessorReceiverType(ktSimpleNameExpression: KtSimpleNameExpression, source: UElement): PsiType? {
        analyseForUast(ktSimpleNameExpression) {
            val ktType =
                ktSimpleNameExpression.resolveAccessorCall()?.targetFunction?.candidates?.singleOrNull()?.receiverType?.type ?: return null
            if (ktType is KtClassErrorType) return null
            return toPsiType(ktType, source, ktSimpleNameExpression, ktSimpleNameExpression.typeOwnerKind, boxed = true)
        }
    }

    override fun getDoubleColonReceiverType(ktDoubleColonExpression: KtDoubleColonExpression, source: UElement): PsiType? {
        analyseForUast(ktDoubleColonExpression) {
            val receiverKtType = ktDoubleColonExpression.getReceiverKtType() ?: return null
            return toPsiType(receiverKtType, source, ktDoubleColonExpression, ktDoubleColonExpression.typeOwnerKind, boxed = true)
        }
    }

    override fun getCommonSupertype(left: KtExpression, right: KtExpression, uExpression: UExpression): PsiType? {
        val ktElement = uExpression.sourcePsi as? KtExpression ?: return null
        analyseForUast(ktElement) {
            val leftType = left.getKtType() ?: return null
            val rightType = right.getKtType()  ?: return null
            val commonSuperType = commonSuperType(listOf(leftType, rightType)) ?: return null
            return toPsiType(commonSuperType, uExpression, ktElement, ktElement.typeOwnerKind)
        }
    }

    override fun getType(ktExpression: KtExpression, source: UElement): PsiType? {
        analyseForUast(ktExpression) {
            val ktType = ktExpression.getKtType() ?: return null
            return toPsiType(ktType, source, ktExpression, ktExpression.typeOwnerKind)
        }
    }

    override fun getType(ktDeclaration: KtDeclaration, source: UElement): PsiType? {
        analyseForUast(ktDeclaration) {
            return toPsiType(ktDeclaration.getReturnKtType(), source, ktDeclaration, ktDeclaration.typeOwnerKind)
        }
    }

    override fun getType(ktDeclaration: KtDeclaration, lightDeclaration: PsiModifierListOwner?): PsiType? {
        analyseForUast(ktDeclaration) {
            return toPsiType(ktDeclaration.getReturnKtType(), lightDeclaration, ktDeclaration, ktDeclaration.typeOwnerKind)
        }
    }

    override fun getFunctionType(ktFunction: KtFunction, source: UElement): PsiType? {
        analyseForUast(ktFunction) {
            return toPsiType(ktFunction.getFunctionalType(), source, ktFunction, ktFunction.typeOwnerKind)
        }
    }

    override fun getFunctionalInterfaceType(uLambdaExpression: KotlinULambdaExpression): PsiType? {
        val sourcePsi = uLambdaExpression.sourcePsi
        analyseForUast(sourcePsi) {
            val samType = sourcePsi.getExpectedType()
                ?.takeIf { it !is KtClassErrorType && it.isFunctionalInterfaceType }
                ?.lowerBoundIfFlexible()
                ?: return null
            return toPsiType(samType, uLambdaExpression, sourcePsi, sourcePsi.typeOwnerKind)
        }
    }

    override fun nullability(psiElement: PsiElement): TypeNullability? {
        if (psiElement is KtTypeReference) {
            analyseForUast(psiElement) {
                nullability(psiElement.getKtType())?.let { return it }
            }
        }
        if (psiElement is KtCallableDeclaration) {
            psiElement.typeReference?.let { typeReference ->
                analyseForUast(typeReference) {
                    nullability(typeReference.getKtType())?.let { return it }
                }
            }
        }
        if (psiElement is KtProperty) {
            psiElement.initializer?.let { propertyInitializer ->
                analyseForUast(propertyInitializer) {
                    nullability(propertyInitializer.getKtType())?.let { return it }
                }
            }
            psiElement.delegateExpression?.let { delegatedExpression ->
                analyseForUast(delegatedExpression) {
                    val typeArgument = (delegatedExpression.getKtType() as? KtNonErrorClassType)?.typeArguments?.firstOrNull()
                    nullability((typeArgument as? KtTypeArgumentWithVariance)?.type)?.let { return it }
                }
            }
        }
        psiElement.getParentOfType<KtProperty>(false)?.let { property ->
            property.typeReference?.let { typeReference ->
                analyseForUast(typeReference) {
                    nullability(typeReference.getKtType())
                }
            } ?:
            property.initializer?.let { propertyInitializer ->
                analyseForUast(propertyInitializer) {
                    nullability(propertyInitializer.getKtType())
                }
            }
        }?.let { return it }
        return null
    }

    override fun evaluate(uExpression: UExpression): Any? {
        val ktExpression = uExpression.sourcePsi as? KtExpression ?: return null
        analyseForUast(ktExpression) {
            return (ktExpression.evaluate() as? KtLiteralConstantValue<*>)?.toConst()
        }
    }
}
