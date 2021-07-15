// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.ParameterDescriptor
import org.jetbrains.kotlin.descriptors.SimpleFunctionDescriptor
import org.jetbrains.kotlin.idea.frontend.api.KtTypeArgumentWithVariance
import org.jetbrains.kotlin.idea.frontend.api.analyseForUast
import org.jetbrains.kotlin.idea.frontend.api.types.*
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.load.kotlin.TypeMappingMode
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.types.typeUtil.TypeNullability
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UastErrorType
import org.jetbrains.uast.kotlin.internal.toPsiMethod
import org.jetbrains.uast.kotlin.psi.UastKotlinPsiParameterBase

interface FirKotlinUastResolveProviderService : BaseKotlinUastResolveProviderService {
    override val baseKotlinConverter: BaseKotlinConverter
        get() = FirKotlinConverter

    override fun convertParent(uElement: UElement): UElement? {
        // TODO
        return null
    }

    override fun convertParent(uElement: UElement, parent: PsiElement?): UElement? {
        TODO("Not yet implemented")
    }

    override fun getArgumentForParameter(ktCallElement: KtCallElement, index: Int, parent: UElement): UExpression? {
        TODO("Not yet implemented")
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
        parametersSelector: CallableDescriptor.() -> List<ParameterDescriptor>
    ): List<KotlinUParameter> {
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
            is KtBinaryExpression -> {
                analyseForUast(ktElement) {
                    return ktElement.resolveCall()?.toPsiMethod()
                }
            }
            is KtUnaryExpression -> {
                analyseForUast(ktElement) {
                    return ktElement.resolveCall()?.toPsiMethod()
                }
            }
            else ->
                return null
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
        analyseForUast(ktTypeReference) {
            return ktTypeReference.getPsiType(TypeMappingMode.DEFAULT_UAST)
        }
    }

    override fun getDoubleColonReceiverType(ktDoubleColonExpression: KtDoubleColonExpression, source: UElement): PsiType? {
        analyseForUast(ktDoubleColonExpression) {
            return ktDoubleColonExpression.getReceiverPsiType(TypeMappingMode.DEFAULT_UAST)
        }
    }

    override fun getCommonSupertype(left: KtExpression, right: KtExpression, uExpression: UExpression): PsiType? {
        // TODO("Not yet implemented")
        return null
    }

    override fun getExpressionType(uExpression: UExpression): PsiType? {
        val ktExpression = uExpression.sourcePsi as? KtExpression ?: return null
        analyseForUast(ktExpression) {
            return ktExpression.getPsiType(TypeMappingMode.DEFAULT_UAST)
        }
    }

    override fun getType(ktExpression: KtExpression, parent: UElement): PsiType? {
        analyseForUast(ktExpression) {
            return ktExpression.getPsiType(TypeMappingMode.DEFAULT_UAST)
        }
    }

    override fun getType(ktDeclaration: KtDeclaration, parent: UElement): PsiType? {
        analyseForUast(ktDeclaration) {
            return ktDeclaration.getPsiType(TypeMappingMode.DEFAULT_UAST)
        }
    }

    override fun getFunctionType(ktFunction: KtFunction, parent: UElement): PsiType? {
        // TODO("Not yet implemented")
        return null
    }

    override fun getFunctionalInterfaceType(uLambdaExpression: KotlinULambdaExpression): PsiType? {
        // TODO("Not yet implemented")
        return null
    }

    override fun nullability(psiElement: PsiElement): TypeNullability? {
        if (psiElement is KtTypeReference) {
            // TODO: KtTypeReference to KtType, and then nullability
        }
        if (psiElement is KtCallableDeclaration) {
            // TODO typeReference to KtType, and then nullability
        }
        if (psiElement is KtProperty) {
            psiElement.initializer?.let { propertyInitializer ->
                analyseForUast(propertyInitializer) {
                    propertyInitializer.getKtType().nullability()?.let { return it }
                }
            }
            psiElement.delegateExpression?.let { delegatedExpression ->
                analyseForUast(delegatedExpression) {
                    val typeArgument = (delegatedExpression.getKtType() as? KtNonErrorClassType)?.typeArguments?.firstOrNull()
                    (typeArgument as? KtTypeArgumentWithVariance)?.type?.nullability()?.let { return it }
                }
            }
        }
        psiElement.getParentOfType<KtProperty>(false)?.let { property ->
            // TODO: try typeReference first
            property.initializer?.let { propertyInitializer ->
                analyseForUast(propertyInitializer) {
                    propertyInitializer.getKtType().nullability()
                }
            }
        }?.let { return it }
        return null
    }

    private fun KtType.nullability(): TypeNullability? {
        if (this !is KtTypeWithNullability) return null
        return when (this.nullability) {
            KtTypeNullability.NON_NULLABLE -> TypeNullability.NOT_NULL
            KtTypeNullability.NULLABLE -> TypeNullability.NULLABLE
        }
    }

    override fun evaluate(uExpression: UExpression): Any? {
        val ktExpression = uExpression.sourcePsi as? KtExpression ?: return null
        analyseForUast(ktExpression) {
            return ktExpression.evaluate()?.value
        }
    }
}
