// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import com.intellij.openapi.components.ServiceManager
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.asJava.elements.KtLightParameterList
import org.jetbrains.kotlin.kdoc.psi.impl.KDocLink
import org.jetbrains.kotlin.kdoc.psi.impl.KDocSection
import org.jetbrains.kotlin.kdoc.psi.impl.KDocTag
import org.jetbrains.kotlin.psi.*
import org.jetbrains.uast.*
import org.jetbrains.uast.kotlin.psi.UastKotlinPsiVariable

interface BaseKotlinConverter {
    fun unwrapElements(element: PsiElement?): PsiElement? = when (element) {
        is KtValueArgumentList -> unwrapElements(element.parent)
        is KtValueArgument -> unwrapElements(element.parent)
        is KtDeclarationModifierList -> unwrapElements(element.parent)
        is KtContainerNode -> unwrapElements(element.parent)
        is KtSimpleNameStringTemplateEntry -> unwrapElements(element.parent)
        is KtLightParameterList -> unwrapElements(element.parent)
        is KtTypeElement -> unwrapElements(element.parent)
        is KtSuperTypeList -> unwrapElements(element.parent)
        is KtFinallySection -> unwrapElements(element.parent)
        is KtAnnotatedExpression -> unwrapElements(element.parent)
        is KtWhenConditionWithExpression -> unwrapElements(element.parent)
        is KDocLink -> unwrapElements(element.parent)
        is KDocSection -> unwrapElements(element.parent)
        is KDocTag -> unwrapElements(element.parent)
        else -> element
    }

    fun convertAnnotation(
        annotationEntry: KtAnnotationEntry,
        givenParent: UElement?
    ): UAnnotation

    fun convertDeclaration(
        element: PsiElement,
        givenParent: UElement?,
        requiredTypes: Array<out Class<out UElement>>
    ): UElement?

    fun convertReceiverParameter(receiver: KtTypeReference): UParameter? {
        val call = (receiver.parent as? KtCallableDeclaration) ?: return null
        if (call.receiverTypeReference != receiver) return null
        return call.toUElementOfType<UMethod>()?.uastParameters?.firstOrNull()
    }

    fun convertExpression(
        expression: KtExpression,
        givenParent: UElement?,
        requiredTypes: Array<out Class<out UElement>>
    ): UExpression?

    fun convertEntry(
        entry: KtStringTemplateEntry,
        givenParent: UElement?,
        requiredTypes: Array<out Class<out UElement>>
    ): UExpression? {
        return with(requiredTypes) {
            if (entry is KtStringTemplateEntryWithExpression) {
                expr<UExpression> {
                    convertOrEmpty(entry.expression, givenParent)
                }
            } else {
                expr<ULiteralExpression> {
                    if (entry is KtEscapeStringTemplateEntry)
                        KotlinStringULiteralExpression(entry, givenParent, entry.unescapedValue)
                    else
                        KotlinStringULiteralExpression(entry, givenParent)
                }
            }
        }
    }

    fun convertVariablesDeclaration(
        psi: KtVariableDeclaration,
        parent: UElement?
    ): UDeclarationsExpression {
        val declarationsExpression = parent as? KotlinUDeclarationsExpression
            ?: psi.parent.toUElementOfType<UDeclarationsExpression>() as? KotlinUDeclarationsExpression
            ?: KotlinUDeclarationsExpression(
                null,
                parent,
                ServiceManager.getService(psi.project, BaseKotlinUastResolveProviderService::class.java),
                psi
            )
        val parentPsiElement = parent?.javaPsi //TODO: looks weird. mb look for the first non-null `javaPsi` in `parents` ?
        val service = ServiceManager.getService(psi.project, BaseKotlinUastResolveProviderService::class.java)
        val variable =
            KotlinUAnnotatedLocalVariable(
                UastKotlinPsiVariable.create(service, psi, parentPsiElement, declarationsExpression),
                psi,
                declarationsExpression
            ) { annotationParent ->
                psi.annotationEntries.map { convertAnnotation(it, annotationParent) }
            }
        return declarationsExpression.apply { declarations = listOf(variable) }
    }

    fun convertWhenCondition(
        condition: KtWhenCondition,
        givenParent: UElement?,
        requiredType: Array<out Class<out UElement>>
    ): UExpression? {
        val project = condition.project
        val service = ServiceManager.getService(project, BaseKotlinUastResolveProviderService::class.java)

        return with(requiredType) {
            when (condition) {
                is KtWhenConditionInRange -> expr<UBinaryExpression> {
                    KotlinCustomUBinaryExpression(condition, givenParent).apply {
                        leftOperand = KotlinStringUSimpleReferenceExpression("it", this, service)
                        operator = when {
                            condition.isNegated -> KotlinBinaryOperators.NOT_IN
                            else -> KotlinBinaryOperators.IN
                        }
                        rightOperand = convertOrEmpty(condition.rangeExpression, this)
                    }
                }
                is KtWhenConditionIsPattern -> expr<UBinaryExpression> {
                    KotlinCustomUBinaryExpressionWithType(condition, givenParent).apply {
                        operand = KotlinStringUSimpleReferenceExpression("it", this, service)
                        operationKind = when {
                            condition.isNegated -> KotlinBinaryExpressionWithTypeKinds.NEGATED_INSTANCE_CHECK
                            else -> UastBinaryExpressionWithTypeKind.InstanceCheck.INSTANCE
                        }
                        typeReference = condition.typeReference?.let {
                            KotlinUTypeReferenceExpression(it, this, service) { service.resolveToType(it, this) ?: UastErrorType }
                        }
                    }
                }

                is KtWhenConditionWithExpression ->
                    condition.expression?.let { convertExpression(it, givenParent, requiredType) }

                else -> expr<UExpression> { UastEmptyExpression(givenParent) }
            }
        }
    }


    fun convertPsiElement(
        element: PsiElement?,
        givenParent: UElement?,
        requiredTypes: Array<out Class<out UElement>>
    ): UElement?

    fun convertOrEmpty(expression: KtExpression?, parent: UElement?): UExpression {
        return expression?.let { convertExpression(it, parent, DEFAULT_EXPRESSION_TYPES_LIST) } ?: UastEmptyExpression(parent)
    }

    fun convertOrNull(expression: KtExpression?, parent: UElement?): UExpression? {
        return if (expression != null) convertExpression(expression, parent, DEFAULT_EXPRESSION_TYPES_LIST) else null
    }
}
