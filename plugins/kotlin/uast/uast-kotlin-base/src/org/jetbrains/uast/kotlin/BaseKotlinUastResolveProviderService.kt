// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import com.intellij.psi.*
import org.jetbrains.kotlin.analysis.api.types.KtTypeNullability
import org.jetbrains.kotlin.psi.*
import org.jetbrains.uast.*

interface BaseKotlinUastResolveProviderService {
    val languagePlugin: UastLanguagePlugin

    // ----------
    // Conversion
    // ----------

    val baseKotlinConverter: BaseKotlinConverter

    fun convertToPsiAnnotation(ktElement: KtElement): PsiAnnotation?

    fun convertParent(uElement: UElement): UElement? {
        return convertParentImpl(this, uElement)
    }

    fun convertParent(uElement: UElement, parent: PsiElement?): UElement? {
        return convertParentImpl(this, uElement, parent)
    }

    fun convertValueArguments(ktCallElement: KtCallElement, parent: UElement): List<UNamedExpression>?

    fun findAttributeValueExpression(uAnnotation: KotlinUAnnotation, arg: ValueArgument): UExpression?

    fun findDefaultValueForAnnotationAttribute(ktCallElement: KtCallElement, name: String): KtExpression?

    fun getArgumentForParameter(ktCallElement: KtCallElement, index: Int, parent: UElement): UExpression?

    fun getImplicitReturn(ktLambdaExpression: KtLambdaExpression, parent: UElement): KotlinUImplicitReturnExpression? {
        val lastExpression = ktLambdaExpression.bodyExpression?.statements?.lastOrNull() ?: return null
        // Skip _explicit_ return.
        if (lastExpression is KtReturnExpression || lastExpression is KtThrowExpression) return null

        /**
         * This is not fully correct in the case of lambda with [Unit] return type and non-[Unit] return type of the last statement:
         * ```kotlin
         * fun foo() {
         *     42.apply {
         *         "str"
         *     }
         * }
         * ```
         * Because here [apply] has [Unit] return type, so we shouldn't have the implicit return here,
         * but we will create it anyway.
         * So effectively, this code will mean:
         * ```kotlin
         * fun foo() {
         *     42.apply {
         *         return@apply "str"
         *     }
         * }
         * ```
         * in terms of UAST what is wrong, but we agree with this behavior because such real type checks are too expensive.
         * But it is correct in the case of [Unit] as a return type of the last statement
         * ```kotlin
         * fun foo() {
         *     42.apply {
         *         return@apply println(this)
         *     }
         * }
         * ```
         */
        return KotlinUImplicitReturnExpression(parent).apply {
            returnExpression = baseKotlinConverter.convertOrEmpty(lastExpression, this)
        }
    }

    fun getImplicitParameters(
        ktLambdaExpression: KtLambdaExpression,
        parent: UElement,
        includeExplicitParameters: Boolean = false
    ): List<KotlinUParameter>

    fun getPsiAnnotations(psiElement: PsiModifierListOwner): Array<PsiAnnotation>

    // ----------
    // Resolution
    // ----------

    fun getReferenceVariants(ktExpression: KtExpression, nameHint: String): Sequence<PsiElement>

    fun resolveBitwiseOperators(ktBinaryExpression: KtBinaryExpression): UastBinaryOperator

    fun resolveCall(ktElement: KtElement): PsiMethod?

    fun resolveSyntheticJavaPropertyAccessorCall(ktSimpleNameExpression: KtSimpleNameExpression): PsiMethod?

    fun isResolvedToExtension(ktCallElement: KtCallElement): Boolean

    fun resolvedFunctionName(ktCallElement: KtCallElement): String?

    fun qualifiedAnnotationName(ktCallElement: KtCallElement): String?

    fun callKind(ktCallElement: KtCallElement): UastCallKind

    fun isAnnotationConstructorCall(ktCallElement: KtCallElement): Boolean

    fun resolveToClassIfConstructorCall(ktCallElement: KtCallElement, source: UElement): PsiClass?

    fun resolveToClass(ktAnnotationEntry: KtAnnotationEntry, source: UElement): PsiClass?

    fun resolveToDeclaration(ktExpression: KtExpression): PsiElement?

    fun resolveToType(ktTypeReference: KtTypeReference, source: UElement, isBoxed: Boolean): PsiType?

    fun resolveToType(ktTypeReference: KtTypeReference, containingLightDeclaration: PsiModifierListOwner?): PsiType?

    // ----------
    // Types
    // ----------

    fun getReceiverType(ktCallElement: KtCallElement, source: UElement): PsiType?

    fun getAccessorReceiverType(ktSimpleNameExpression: KtSimpleNameExpression, source: UElement): PsiType?

    fun getDoubleColonReceiverType(ktDoubleColonExpression: KtDoubleColonExpression, source: UElement): PsiType?

    fun getCommonSupertype(left: KtExpression, right: KtExpression, uExpression: UExpression): PsiType?

    fun getType(ktExpression: KtExpression, source: UElement): PsiType?

    fun getType(ktDeclaration: KtDeclaration, source: UElement): PsiType?

    fun getType(
        ktDeclaration: KtDeclaration,
        containingLightDeclaration: PsiModifierListOwner?,
        isForFake: Boolean = false,
    ): PsiType?

    fun getSuspendContinuationType(
        suspendFunction: KtFunction,
        containingLightDeclaration: PsiModifierListOwner?,
    ): PsiType?

    fun getFunctionType(ktFunction: KtFunction, source: UElement?): PsiType?

    fun getFunctionalInterfaceType(uLambdaExpression: KotlinULambdaExpression): PsiType?

    fun hasInheritedGenericType(psiElement: PsiElement): Boolean

    fun nullability(psiElement: PsiElement): KtTypeNullability?

    // ----------
    // Evaluation
    // ----------

    fun evaluate(uExpression: UExpression): Any?
}
