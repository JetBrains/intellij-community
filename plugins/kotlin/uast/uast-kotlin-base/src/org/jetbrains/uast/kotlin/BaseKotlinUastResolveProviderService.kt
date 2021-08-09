// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import com.intellij.psi.*
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.ParameterDescriptor
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.types.typeUtil.TypeNullability
import org.jetbrains.uast.*

interface BaseKotlinUastResolveProviderService {
    fun isJvmElement(psiElement: PsiElement): Boolean

    val languagePlugin: UastLanguagePlugin

    // ----------
    // Conversion
    // ----------

    val baseKotlinConverter: BaseKotlinConverter

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

    fun getImplicitReturn(ktLambdaExpression: KtLambdaExpression, parent: UElement): KotlinUImplicitReturnExpression?

    fun getImplicitParameters(
        ktLambdaExpression: KtLambdaExpression,
        parent: UElement,
        includeExplicitParameters: Boolean = false
    ): List<KotlinUParameter>

    // ----------
    // Resolution
    // ----------

    fun getReferenceVariants(ktExpression: KtExpression, nameHint: String): Sequence<PsiElement>

    fun resolveCall(ktElement: KtElement): PsiMethod?

    fun isResolvedToExtension(ktCallElement: KtCallElement): Boolean

    fun resolvedFunctionName(ktCallElement: KtCallElement): String?

    fun qualifiedAnnotationName(ktCallElement: KtCallElement): String?

    fun callKind(ktCallElement: KtCallElement): UastCallKind

    fun isAnnotationConstructorCall(ktCallElement: KtCallElement): Boolean

    fun resolveToClassIfConstructorCall(ktCallElement: KtCallElement, source: UElement): PsiClass?

    fun resolveToClass(ktAnnotationEntry: KtAnnotationEntry): PsiClass?

    fun resolveToDeclaration(ktExpression: KtExpression): PsiElement?

    fun resolveToType(ktTypeReference: KtTypeReference, source: UElement): PsiType?

    fun resolveToType(ktTypeReference: KtTypeReference, lightDeclaration: PsiModifierListOwner?): PsiType?

    // ----------
    // Types
    // ----------

    fun getReceiverType(ktCallElement: KtCallElement, source: UElement): PsiType?

    fun getDoubleColonReceiverType(ktDoubleColonExpression: KtDoubleColonExpression, source: UElement): PsiType?

    fun getCommonSupertype(left: KtExpression, right: KtExpression, uExpression: UExpression): PsiType?

    fun getType(ktExpression: KtExpression, source: UElement): PsiType?

    fun getType(ktDeclaration: KtDeclaration, source: UElement): PsiType?

    fun getType(ktDeclaration: KtDeclaration, lightDeclaration: PsiModifierListOwner?): PsiType?

    fun getFunctionType(ktFunction: KtFunction, source: UElement): PsiType?

    fun getFunctionalInterfaceType(uLambdaExpression: KotlinULambdaExpression): PsiType?

    fun nullability(psiElement: PsiElement): TypeNullability?

    // ----------
    // Evaluation
    // ----------

    fun evaluate(uExpression: UExpression): Any?
}
