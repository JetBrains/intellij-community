// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.searching.usages

import com.intellij.psi.PsiElement
import com.intellij.usages.UsageTarget
import com.intellij.usages.impl.rules.UsageType
import com.intellij.usages.impl.rules.UsageTypeProviderEx
import org.jetbrains.kotlin.idea.base.searching.usages.KotlinUsageTypes.toUsageType
import org.jetbrains.kotlin.idea.base.searching.usages.UsageTypeEnum.CALLABLE_REFERENCE
import org.jetbrains.kotlin.idea.base.searching.usages.UsageTypeEnum.CLASS_CAST_TO
import org.jetbrains.kotlin.idea.base.searching.usages.UsageTypeEnum.CLASS_IMPORT
import org.jetbrains.kotlin.idea.base.searching.usages.UsageTypeEnum.CLASS_LOCAL_VAR_DECLARATION
import org.jetbrains.kotlin.idea.base.searching.usages.UsageTypeEnum.CLASS_OBJECT_ACCESS
import org.jetbrains.kotlin.idea.base.searching.usages.UsageTypeEnum.CONSTRUCTOR_DELEGATION_REFERENCE
import org.jetbrains.kotlin.idea.base.searching.usages.UsageTypeEnum.DELEGATE
import org.jetbrains.kotlin.idea.base.searching.usages.UsageTypeEnum.EXTENSION_RECEIVER_TYPE
import org.jetbrains.kotlin.idea.base.searching.usages.UsageTypeEnum.FUNCTION_RETURN_TYPE
import org.jetbrains.kotlin.idea.base.searching.usages.UsageTypeEnum.IMPLICIT_ITERATION
import org.jetbrains.kotlin.idea.base.searching.usages.UsageTypeEnum.IS
import org.jetbrains.kotlin.idea.base.searching.usages.UsageTypeEnum.NAMED_ARGUMENT
import org.jetbrains.kotlin.idea.base.searching.usages.UsageTypeEnum.NON_LOCAL_PROPERTY_TYPE
import org.jetbrains.kotlin.idea.base.searching.usages.UsageTypeEnum.PACKAGE_DIRECTIVE
import org.jetbrains.kotlin.idea.base.searching.usages.UsageTypeEnum.PACKAGE_MEMBER_ACCESS
import org.jetbrains.kotlin.idea.base.searching.usages.UsageTypeEnum.PROPERTY_DELEGATION
import org.jetbrains.kotlin.idea.base.searching.usages.UsageTypeEnum.READ
import org.jetbrains.kotlin.idea.base.searching.usages.UsageTypeEnum.RECEIVER
import org.jetbrains.kotlin.idea.base.searching.usages.UsageTypeEnum.SUPER_TYPE
import org.jetbrains.kotlin.idea.base.searching.usages.UsageTypeEnum.SUPER_TYPE_QUALIFIER
import org.jetbrains.kotlin.idea.base.searching.usages.UsageTypeEnum.TYPE_ALIAS
import org.jetbrains.kotlin.idea.base.searching.usages.UsageTypeEnum.TYPE_CONSTRAINT
import org.jetbrains.kotlin.idea.base.searching.usages.UsageTypeEnum.TYPE_PARAMETER
import org.jetbrains.kotlin.idea.base.searching.usages.UsageTypeEnum.USAGE_IN_STRING_LITERAL
import org.jetbrains.kotlin.idea.base.searching.usages.UsageTypeEnum.VALUE_PARAMETER_TYPE
import org.jetbrains.kotlin.idea.base.searching.usages.UsageTypeEnum.WRITE
import org.jetbrains.kotlin.idea.references.readWriteAccess
import org.jetbrains.kotlin.psi.KtBinaryExpressionWithTypeRHS
import org.jetbrains.kotlin.psi.KtCallableReferenceExpression
import org.jetbrains.kotlin.psi.KtConstructorDelegationReferenceExpression
import org.jetbrains.kotlin.psi.KtDelegatedSuperTypeEntry
import org.jetbrains.kotlin.psi.KtDestructuringDeclarationEntry
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtForExpression
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtIsExpression
import org.jetbrains.kotlin.psi.KtPackageDirective
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyDelegate
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtSuperExpression
import org.jetbrains.kotlin.psi.KtSuperTypeListEntry
import org.jetbrains.kotlin.psi.KtTypeAlias
import org.jetbrains.kotlin.psi.KtTypeConstraint
import org.jetbrains.kotlin.psi.KtTypeParameter
import org.jetbrains.kotlin.psi.KtTypeProjection
import org.jetbrains.kotlin.psi.KtValueArgumentName
import org.jetbrains.kotlin.psi.KtWhenConditionIsPattern
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getParentOfTypeAndBranch
import org.jetbrains.kotlin.psi.psiUtil.isAncestor
import org.jetbrains.kotlin.resolve.references.ReferenceAccess

abstract class KotlinUsageTypeProvider : UsageTypeProviderEx {

    abstract fun getUsageTypeEnumByReference(refExpr: KtReferenceExpression): UsageTypeEnum?

    private fun getUsageTypeEnum(element: PsiElement?): UsageTypeEnum? {
        when (element) {
            is KtForExpression -> return IMPLICIT_ITERATION
            is KtDestructuringDeclarationEntry -> return READ
            is KtPropertyDelegate -> return PROPERTY_DELEGATION
            is KtStringTemplateExpression -> return USAGE_IN_STRING_LITERAL
            is KtConstructorDelegationReferenceExpression -> return CONSTRUCTOR_DELEGATION_REFERENCE
        }

        val refExpr = element?.getNonStrictParentOfType<KtReferenceExpression>() ?: return null

        return getCommonUsageType(refExpr) ?: getUsageTypeEnumByReference(refExpr)
    }

    override fun getUsageType(element: PsiElement): UsageType? = getUsageType(element, UsageTarget.EMPTY_ARRAY)

    override fun getUsageType(element: PsiElement, targets: Array<out UsageTarget>): UsageType? {
        val usageType = getUsageTypeEnum(element) ?: return null
        return usageType.toUsageType()
    }

    private fun getCommonUsageType(refExpr: KtReferenceExpression): UsageTypeEnum? = when {
        refExpr.getNonStrictParentOfType<KtImportDirective>() != null -> CLASS_IMPORT
        refExpr.getParentOfTypeAndBranch<KtCallableReferenceExpression> { callableReference } != null -> CALLABLE_REFERENCE
        else -> null
    }

    protected fun getClassUsageType(refExpr: KtReferenceExpression): UsageTypeEnum? {
        if (refExpr.getNonStrictParentOfType<KtTypeProjection>() != null) return TYPE_PARAMETER

        val property = refExpr.getNonStrictParentOfType<KtProperty>()
        if (property != null) {
            when {
                property.typeReference.isAncestor(refExpr) ->
                    return if (property.isLocal) CLASS_LOCAL_VAR_DECLARATION else NON_LOCAL_PROPERTY_TYPE

                property.receiverTypeReference.isAncestor(refExpr) ->
                    return EXTENSION_RECEIVER_TYPE
            }
        }

        val function = refExpr.getNonStrictParentOfType<KtFunction>()
        if (function != null) {
            when {
                function.typeReference.isAncestor(refExpr) ->
                    return FUNCTION_RETURN_TYPE
                function.receiverTypeReference.isAncestor(refExpr) ->
                    return EXTENSION_RECEIVER_TYPE
            }
        }

        return when {
            refExpr.getParentOfTypeAndBranch<KtTypeParameter> { extendsBound } != null || refExpr.getParentOfTypeAndBranch<KtTypeConstraint> { boundTypeReference } != null -> TYPE_CONSTRAINT

            refExpr is KtSuperTypeListEntry || refExpr.getParentOfTypeAndBranch<KtSuperTypeListEntry> { typeReference } != null -> SUPER_TYPE

            refExpr.getParentOfTypeAndBranch<KtParameter> { typeReference } != null -> VALUE_PARAMETER_TYPE

            refExpr.getParentOfTypeAndBranch<KtIsExpression> { typeReference } != null || refExpr.getParentOfTypeAndBranch<KtWhenConditionIsPattern> { typeReference } != null -> IS

            with(refExpr.getParentOfTypeAndBranch<KtBinaryExpressionWithTypeRHS> { right }) {
                val opType = this?.operationReference?.getReferencedNameElementType()
                opType == org.jetbrains.kotlin.lexer.KtTokens.AS_KEYWORD || opType == org.jetbrains.kotlin.lexer.KtTokens.AS_SAFE
            } -> CLASS_CAST_TO

            with(refExpr.getNonStrictParentOfType<KtDotQualifiedExpression>()) {
                when {
                    this == null -> {
                        false
                    }
                    receiverExpression == refExpr -> {
                        true
                    }
                    else -> {
                        selectorExpression == refExpr
                                && getParentOfTypeAndBranch<KtDotQualifiedExpression>(strict = true) { receiverExpression } != null
                    }
                }
            } -> CLASS_OBJECT_ACCESS

            refExpr.getParentOfTypeAndBranch<KtSuperExpression> { superTypeQualifier } != null -> SUPER_TYPE_QUALIFIER

            refExpr.getParentOfTypeAndBranch<KtTypeAlias> { getTypeReference() } != null -> TYPE_ALIAS

            else -> null
        }
    }

    protected fun getVariableUsageType(refExpr: KtReferenceExpression): UsageTypeEnum {
        if (refExpr.getParentOfTypeAndBranch<KtDelegatedSuperTypeEntry> { delegateExpression } != null) return DELEGATE

        if (refExpr.parent is KtValueArgumentName) return NAMED_ARGUMENT

        val dotQualifiedExpression = refExpr.getNonStrictParentOfType<KtDotQualifiedExpression>()

        if (dotQualifiedExpression != null) {
            val parent = dotQualifiedExpression.parent
            when {
                dotQualifiedExpression.receiverExpression.isAncestor(refExpr) ->
                    return RECEIVER

                parent is KtDotQualifiedExpression && parent.receiverExpression.isAncestor(refExpr) ->
                    return RECEIVER
            }
        }

        return when (refExpr.readWriteAccess(useResolveForReadWrite = true)) {
            ReferenceAccess.READ -> READ
            ReferenceAccess.WRITE, ReferenceAccess.READ_WRITE -> WRITE
        }
    }

    protected fun getPackageUsageType(refExpr: KtReferenceExpression): UsageTypeEnum? = when {
        refExpr.getNonStrictParentOfType<KtPackageDirective>() != null -> PACKAGE_DIRECTIVE
        refExpr.getNonStrictParentOfType<KtQualifiedExpression>() != null -> PACKAGE_MEMBER_ACCESS
        else -> getClassUsageType(refExpr)
    }
}

enum class UsageTypeEnum {
    TYPE_CONSTRAINT,
    VALUE_PARAMETER_TYPE,
    NON_LOCAL_PROPERTY_TYPE,
    FUNCTION_RETURN_TYPE,
    SUPER_TYPE,
    IS,
    CLASS_OBJECT_ACCESS,
    COMPANION_OBJECT_ACCESS,
    EXTENSION_RECEIVER_TYPE,
    SUPER_TYPE_QUALIFIER,
    TYPE_ALIAS,

    FUNCTION_CALL,
    IMPLICIT_GET,
    IMPLICIT_SET,
    IMPLICIT_INVOKE,
    IMPLICIT_ITERATION,
    PROPERTY_DELEGATION,
    SUPER_DELEGATION,

    RECEIVER,
    DELEGATE,

    PACKAGE_DIRECTIVE,
    PACKAGE_MEMBER_ACCESS,

    CALLABLE_REFERENCE,

    READ,
    WRITE,
    CLASS_IMPORT,
    CLASS_LOCAL_VAR_DECLARATION,
    TYPE_PARAMETER,
    CLASS_CAST_TO,
    ANNOTATION,
    CLASS_NEW_OPERATOR,
    NAMED_ARGUMENT,

    USAGE_IN_STRING_LITERAL,

    CONSTRUCTOR_DELEGATION_REFERENCE,
}
