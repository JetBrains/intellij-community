// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import com.intellij.psi.*
import org.jetbrains.kotlin.idea.frontend.api.KtTypeArgumentWithVariance
import org.jetbrains.kotlin.idea.frontend.api.analyseForUast
import org.jetbrains.kotlin.idea.frontend.api.calls.KtAnnotationCall
import org.jetbrains.kotlin.idea.frontend.api.calls.KtCallWithArguments
import org.jetbrains.kotlin.idea.frontend.api.symbols.KtConstructorSymbol
import org.jetbrains.kotlin.idea.frontend.api.symbols.markers.KtNamedSymbol
import org.jetbrains.kotlin.idea.frontend.api.types.*
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.load.kotlin.TypeMappingMode
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.types.typeUtil.TypeNullability
import org.jetbrains.uast.*
import org.jetbrains.uast.kotlin.internal.firKotlinUastPlugin
import org.jetbrains.uast.kotlin.internal.nullability
import org.jetbrains.uast.kotlin.internal.toPsiClass
import org.jetbrains.uast.kotlin.internal.toPsiMethod
import org.jetbrains.uast.kotlin.psi.UastKotlinPsiParameterBase

interface FirKotlinUastResolveProviderService : BaseKotlinUastResolveProviderService {

    override val languagePlugin: UastLanguagePlugin
        get() = firKotlinUastPlugin

    override val baseKotlinConverter: BaseKotlinConverter
        get() = FirKotlinConverter

    override fun convertValueArguments(ktCallElement: KtCallElement, parent: UElement): List<UNamedExpression>? {
        analyseForUast(ktCallElement) {
            val argumentMapping = (ktCallElement.resolveCall() as? KtCallWithArguments)?.argumentMapping ?: return null
            return argumentMapping.entries.map {
                val name = it.value.name.asString()
                // TODO: it.key.isSpread() ?
                KotlinUNamedExpression.create(name, it.key, parent)
            }
        }
    }

    override fun findAttributeValueExpression(uAnnotation: KotlinUAnnotation, arg: ValueArgument): UExpression? {
        val annotationEntry = uAnnotation.sourcePsi
        analyseForUast(annotationEntry) {
            val resolvedAnnotationCall = annotationEntry.resolveCall() as? KtAnnotationCall ?: return null
            val parameter = resolvedAnnotationCall.argumentMapping[arg] ?: return null
            val namedExpression = uAnnotation.attributeValues.find { it.name == parameter.name.asString() }
            return namedExpression?.expression as? KotlinUVarargExpression ?: namedExpression
        }
    }

    override fun findDefaultValueForAnnotationAttribute(ktCallElement: KtCallElement, name: String): KtExpression? {
        analyseForUast(ktCallElement) {
            val resolvedAnnotationCall = ktCallElement.resolveCall() as? KtAnnotationCall ?: return null
            val resolvedAnnotationConstructorSymbol =
                resolvedAnnotationCall.targetFunction.candidates.singleOrNull() as? KtConstructorSymbol ?: return null
            val parameter = resolvedAnnotationConstructorSymbol.valueParameters.find { it.name.asString() == name } ?: return null
            return (parameter.psi as? KtParameter)?.defaultValue
        }
    }

    override fun getArgumentForParameter(ktCallElement: KtCallElement, index: Int, parent: UElement): UExpression? {
        // TODO: KtValueParameterSymbol doesn't have a way to retrieve 0-based index of the corresponding parameter.
        return null
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
            val resolvedAnnotationCall = ktCallElement.resolveCall() as? KtAnnotationCall ?: return null
            val resolvedAnnotationConstructorSymbol =
                resolvedAnnotationCall.targetFunction.candidates.singleOrNull() as? KtConstructorSymbol ?: return null
            return resolvedAnnotationConstructorSymbol.containingClassIdIfNonLocal
                ?.asSingleFqName()
                ?.toString()
        }
    }

    override fun callKind(ktCallElement: KtCallElement): UastCallKind {
        analyseForUast(ktCallElement) {
            val resolvedFunctionLikeSymbol =
                ktCallElement.resolveCall()?.targetFunction?.candidates?.singleOrNull() ?: return UastCallKind.METHOD_CALL
            return when (resolvedFunctionLikeSymbol) {
                is KtConstructorSymbol -> UastCallKind.CONSTRUCTOR_CALL
                // TODO: NESTED_ARRAY_INITIALIZER
                else -> UastCallKind.METHOD_CALL
            }
        }
    }

    override fun isAnnotationConstructorCall(ktCallElement: KtCallElement): Boolean {
        analyseForUast(ktCallElement) {
            val resolvedAnnotationCall = ktCallElement.resolveCall() as? KtAnnotationCall ?: return false
            val resolvedAnnotationConstructorSymbol =
                resolvedAnnotationCall.targetFunction.candidates.singleOrNull() as? KtConstructorSymbol ?: return false
            val psiClass = toPsiClass(resolvedAnnotationConstructorSymbol.annotatedType.type, ktCallElement) ?: return false
            return psiClass.isAnnotationType
        }
    }

    override fun resolveToClassIfConstructorCall(ktCallElement: KtCallElement, source: UElement): PsiClass? {
        analyseForUast(ktCallElement) {
            val resolvedFunctionLikeSymbol = ktCallElement.resolveCall()?.targetFunction?.candidates?.singleOrNull() ?: return null
            return when (resolvedFunctionLikeSymbol) {
                is KtConstructorSymbol -> toPsiClass(resolvedFunctionLikeSymbol.annotatedType.type, ktCallElement)
                // TODO: SAM constructor
                else -> null
            }
        }
    }

    override fun resolveToClass(ktAnnotationEntry: KtAnnotationEntry): PsiClass? {
        analyseForUast(ktAnnotationEntry) {
            val resolvedAnnotationCall = ktAnnotationEntry.resolveCall() as? KtAnnotationCall ?: return null
            val resolvedAnnotationConstructorSymbol =
                resolvedAnnotationCall.targetFunction.candidates.singleOrNull() as? KtConstructorSymbol ?: return null
            return toPsiClass(resolvedAnnotationConstructorSymbol.annotatedType.type, ktAnnotationEntry)
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

    override fun resolveToType(ktTypeReference: KtTypeReference, source: UElement): PsiType? {
        return resolveToType(ktTypeReference)
    }

    override fun resolveToType(ktTypeReference: KtTypeReference, lightDeclaration: PsiModifierListOwner?): PsiType? {
        return resolveToType(ktTypeReference)
    }

    private fun resolveToType(ktTypeReference: KtTypeReference): PsiType? {
        analyseForUast(ktTypeReference) {
            val ktType = ktTypeReference.getKtType()
            if (ktType is KtClassErrorType) return null
            return ktType.asPsiType(ktTypeReference, TypeMappingMode.DEFAULT_UAST)
        }
    }

    override fun getReceiverType(ktCallElement: KtCallElement, source: UElement): PsiType? {
        analyseForUast(ktCallElement) {
            val ktType = ktCallElement.resolveCall()?.targetFunction?.candidates?.singleOrNull()?.receiverType?.type ?: return null
            if (ktType is KtClassErrorType) return null
            return ktType.asPsiType(ktCallElement, TypeMappingMode.DEFAULT_UAST)
        }
    }

    override fun getDoubleColonReceiverType(ktDoubleColonExpression: KtDoubleColonExpression, source: UElement): PsiType? {
        analyseForUast(ktDoubleColonExpression) {
            return ktDoubleColonExpression.getReceiverKtType()?.asPsiType(ktDoubleColonExpression, TypeMappingMode.DEFAULT_UAST)
        }
    }

    override fun getCommonSupertype(left: KtExpression, right: KtExpression, uExpression: UExpression): PsiType? {
        val ktElement = uExpression.sourcePsi as? KtExpression ?: return null
        analyseForUast(ktElement) {
            val leftType = left.getKtType() ?: return null
            val rightType = right.getKtType()  ?: return null
            return commonSuperType(listOf(leftType, rightType))?.asPsiType(ktElement, TypeMappingMode.DEFAULT_UAST)
        }
    }

    override fun getType(ktExpression: KtExpression, source: UElement): PsiType? {
        analyseForUast(ktExpression) {
            return ktExpression.getKtType()?.asPsiType(ktExpression, TypeMappingMode.DEFAULT_UAST)
        }
    }

    override fun getType(ktDeclaration: KtDeclaration, source: UElement): PsiType? {
        return getType(ktDeclaration)
    }

    override fun getType(ktDeclaration: KtDeclaration, lightDeclaration: PsiModifierListOwner?): PsiType? {
        return getType(ktDeclaration)
    }

    private fun getType(ktDeclaration: KtDeclaration): PsiType? {
        analyseForUast(ktDeclaration) {
            return ktDeclaration.getReturnKtType().asPsiType(ktDeclaration, TypeMappingMode.DEFAULT_UAST)
        }
    }

    override fun getFunctionType(ktFunction: KtFunction, source: UElement): PsiType? {
        analyseForUast(ktFunction) {
            return ktFunction.getFunctionalType().asPsiType(ktFunction, TypeMappingMode.DEFAULT_UAST)
        }
    }

    override fun getFunctionalInterfaceType(uLambdaExpression: KotlinULambdaExpression): PsiType? {
        // TODO("Not yet implemented")
        return null
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
            return ktExpression.evaluate()?.value
        }
    }
}
