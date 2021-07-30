// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import com.intellij.openapi.components.ServiceManager
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiEnumConstant
import com.intellij.psi.PsiParameterList
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.kotlin.asJava.LightClassUtil
import org.jetbrains.kotlin.asJava.elements.KtLightElementBase
import org.jetbrains.kotlin.asJava.elements.KtLightField
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.kdoc.psi.impl.KDocLink
import org.jetbrains.kotlin.kdoc.psi.impl.KDocName
import org.jetbrains.kotlin.kdoc.psi.impl.KDocSection
import org.jetbrains.kotlin.kdoc.psi.impl.KDocTag
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.uast.*
import org.jetbrains.uast.kotlin.psi.UastKotlinPsiParameter
import org.jetbrains.uast.kotlin.psi.UastKotlinPsiVariable

interface BaseKotlinConverter {
    fun unwrapElements(element: PsiElement?): PsiElement? = when (element) {
        is KtValueArgumentList -> unwrapElements(element.parent)
        is KtValueArgument -> unwrapElements(element.parent)
        is KtDeclarationModifierList -> unwrapElements(element.parent)
        is KtContainerNode -> unwrapElements(element.parent)
        is KtSimpleNameStringTemplateEntry -> unwrapElements(element.parent)
        is PsiParameterList -> unwrapElements(element.parent)
        is KtTypeArgumentList -> unwrapElements(element.parent)
        is KtTypeProjection -> unwrapElements(element.parent)
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
    ): UAnnotation {
        return KotlinUAnnotation(annotationEntry, givenParent)
    }

    fun convertDeclaration(
        element: PsiElement,
        givenParent: UElement?,
        requiredTypes: Array<out Class<out UElement>>
    ): UElement?

    fun convertDeclarationOrElement(
        element: PsiElement,
        givenParent: UElement?,
        expectedTypes: Array<out Class<out UElement>>
    ): UElement? {
        return if (element is UElement) element
        else convertDeclaration(element, givenParent, expectedTypes)
            ?: convertPsiElement(element, givenParent, expectedTypes)
    }

    fun convertClassOrObject(
        element: KtClassOrObject,
        givenParent: UElement?,
        requiredTypes: Array<out Class<out UElement>>
    ): Sequence<UElement> {
        val ktLightClass = element.toLightClass() ?: return emptySequence()
        val uClass = KotlinUClass.create(ktLightClass, givenParent)
        return requiredTypes.accommodate(
            // Class
            alternative { uClass },
            // Object
            alternative primaryConstructor@{
                val primaryConstructor = element.primaryConstructor ?: return@primaryConstructor null
                uClass.methods.asSequence()
                    .filter { it.sourcePsi == primaryConstructor }
                    .firstOrNull()
            }
        )
    }

    fun convertToPropertyAlternatives(
        methods: LightClassUtil.PropertyAccessorsPsiMethods?,
        givenParent: UElement?
    ): Array<UElementAlternative<*>> {
        return if (methods != null)
            arrayOf(
                alternative { methods.backingField?.let { KotlinUField(it, getKotlinMemberOrigin(it), givenParent) } },
                alternative { methods.getter?.let { convertDeclaration(it, givenParent, arrayOf(UMethod::class.java)) as? UMethod } },
                alternative { methods.setter?.let { convertDeclaration(it, givenParent, arrayOf(UMethod::class.java)) as? UMethod } }
            )
        else emptyArray()
    }

    fun convertNonLocalProperty(
        property: KtProperty,
        givenParent: UElement?,
        requiredTypes: Array<out Class<out UElement>>
    ): Sequence<UElement> {
        return requiredTypes.accommodate(
            *convertToPropertyAlternatives(LightClassUtil.getLightClassPropertyMethods(property), givenParent)
        )
    }

    fun convertEnumEntry(original: KtEnumEntry, givenParent: UElement?): UElement? {
        return LightClassUtil.getLightClassBackingField(original)?.let { psiField ->
            if (psiField is KtLightField && psiField is PsiEnumConstant) {
                KotlinUEnumConstant(psiField, psiField.kotlinOrigin, givenParent)
            } else {
                null
            }
        }
    }

    fun convertReceiverParameter(receiver: KtTypeReference): UParameter? {
        val call = (receiver.parent as? KtCallableDeclaration) ?: return null
        if (call.receiverTypeReference != receiver) return null
        return call.toUElementOfType<UMethod>()?.uastParameters?.firstOrNull()
    }

    fun forceUInjectionHost(): Boolean

    fun convertExpression(
        expression: KtExpression,
        givenParent: UElement?,
        requiredTypes: Array<out Class<out UElement>>
    ): UExpression?

    fun convertStringTemplateEntry(
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
                psi
            )
        val parentPsiElement = parent?.javaPsi //TODO: looks weird. mb look for the first non-null `javaPsi` in `parents` ?
        val variable =
            KotlinUAnnotatedLocalVariable(
                UastKotlinPsiVariable.create(psi, parentPsiElement, declarationsExpression),
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
        return with(requiredType) {
            when (condition) {
                is KtWhenConditionInRange -> expr<UBinaryExpression> {
                    KotlinCustomUBinaryExpression(condition, givenParent).apply {
                        leftOperand = KotlinStringUSimpleReferenceExpression("it", this)
                        operator = when {
                            condition.isNegated -> KotlinBinaryOperators.NOT_IN
                            else -> KotlinBinaryOperators.IN
                        }
                        rightOperand = convertOrEmpty(condition.rangeExpression, this)
                    }
                }
                is KtWhenConditionIsPattern -> expr<UBinaryExpression> {
                    KotlinCustomUBinaryExpressionWithType(condition, givenParent).apply {
                        operand = KotlinStringUSimpleReferenceExpression("it", this)
                        operationKind = when {
                            condition.isNegated -> KotlinBinaryExpressionWithTypeKinds.NEGATED_INSTANCE_CHECK
                            else -> UastBinaryExpressionWithTypeKind.InstanceCheck.INSTANCE
                        }
                        typeReference = condition.typeReference?.let {
                            val service = ServiceManager.getService(BaseKotlinUastResolveProviderService::class.java)
                            KotlinUTypeReferenceExpression(it, this) { service.resolveToType(it, this) ?: UastErrorType }
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
    ): UElement? {
        if (element == null) return null

        fun <P : PsiElement> build(ctor: (P, UElement?) -> UElement): () -> UElement? = {
            @Suppress("UNCHECKED_CAST")
            ctor(element as P, givenParent)
        }

        return with(requiredTypes) {
            when (element) {
                is KtParameterList -> {
                    el<UDeclarationsExpression> {
                        val declarationsExpression = KotlinUDeclarationsExpression(null, givenParent, null)
                        declarationsExpression.apply {
                            declarations = element.parameters.mapIndexed { i, p ->
                                KotlinUParameter(UastKotlinPsiParameter.create(p, element, declarationsExpression, i), p, this)
                            }
                        }
                    }
                }
                is KtClassBody -> {
                    el<UExpressionList>(build(KotlinUExpressionList.Companion::createClassBody))
                }
                is KtCatchClause -> {
                    el<UCatchClause>(build(::KotlinUCatchClause))
                }
                is KtVariableDeclaration -> {
                    if (element is KtProperty && !element.isLocal) {
                        convertNonLocalProperty(element, givenParent, this).firstOrNull()
                    } else {
                        el<UVariable> { convertVariablesDeclaration(element, givenParent).declarations.singleOrNull() }
                            ?: expr<UDeclarationsExpression> { convertExpression(element, givenParent, requiredTypes) }
                    }
                }
                is KtExpression -> {
                    convertExpression(element, givenParent, requiredTypes)
                }
                is KtLambdaArgument -> {
                    element.getLambdaExpression()?.let { convertExpression(it, givenParent, requiredTypes) }
                }
                is KtLightElementBase -> {
                    when (val expression = element.kotlinOrigin) {
                        is KtExpression -> convertExpression(expression, givenParent, requiredTypes)
                        else -> el<UExpression> { UastEmptyExpression(givenParent) }
                    }
                }
                is KtLiteralStringTemplateEntry, is KtEscapeStringTemplateEntry -> {
                    el<ULiteralExpression>(build(::KotlinStringULiteralExpression))
                }
                is KtStringTemplateEntry -> {
                    element.expression?.let { convertExpression(it, givenParent, requiredTypes) }
                        ?: expr<UExpression> { UastEmptyExpression(givenParent) }
                }
                is KtWhenEntry -> {
                    el<USwitchClauseExpressionWithBody>(build(::KotlinUSwitchEntry))
                }
                is KtWhenCondition -> {
                    convertWhenCondition(element, givenParent, requiredTypes)
                }
                is KtTypeReference -> {
                    requiredTypes.accommodate(
                        alternative { KotlinUTypeReferenceExpression(element, givenParent) },
                        alternative { convertReceiverParameter(element) }
                    ).firstOrNull()
                }
                is KtConstructorDelegationCall -> {
                    el<UCallExpression> { KotlinUFunctionCallExpression(element, givenParent) }
                }
                is KtSuperTypeCallEntry -> {
                    el<UExpression> {
                        (element.getParentOfType<KtClassOrObject>(true)?.parent as? KtObjectLiteralExpression)
                            ?.toUElementOfType<UExpression>()
                            ?: KotlinUFunctionCallExpression(element, givenParent)
                    }
                }
                is KtImportDirective -> {
                    el<UImportStatement>(build(::KotlinUImportStatement))
                }
                is PsiComment -> {
                    el<UComment>(build(::UComment))
                }
                is KDocName -> {
                    if (element.getQualifier() == null)
                        el<USimpleNameReferenceExpression> {
                            element.lastChild?.let { psiIdentifier ->
                                KotlinStringUSimpleReferenceExpression(psiIdentifier.text, givenParent, element, element)
                            }
                        }
                    else el<UQualifiedReferenceExpression>(build(::KotlinDocUQualifiedReferenceExpression))
                }
                is LeafPsiElement -> {
                    when {
                        element.elementType in identifiersTokens -> {
                            if (element.elementType != KtTokens.OBJECT_KEYWORD ||
                                element.getParentOfType<KtObjectDeclaration>(false)?.nameIdentifier == null
                            )
                                el<UIdentifier>(build(::KotlinUIdentifier))
                            else null
                        }
                        element.elementType in KtTokens.OPERATIONS && element.parent is KtOperationReferenceExpression -> {
                            el<UIdentifier>(build(::KotlinUIdentifier))
                        }
                        element.elementType == KtTokens.LBRACKET && element.parent is KtCollectionLiteralExpression -> {
                            el<UIdentifier> {
                                UIdentifier(
                                    element,
                                    KotlinUCollectionLiteralExpression(element.parent as KtCollectionLiteralExpression, null)
                                )
                            }
                        }
                        else -> null
                    }
                }
                else -> null
            }
        }
    }

    fun convertOrEmpty(expression: KtExpression?, parent: UElement?): UExpression {
        return expression?.let { convertExpression(it, parent, DEFAULT_EXPRESSION_TYPES_LIST) } ?: UastEmptyExpression(parent)
    }

    fun convertOrNull(expression: KtExpression?, parent: UElement?): UExpression? {
        return if (expression != null) convertExpression(expression, parent, DEFAULT_EXPRESSION_TYPES_LIST) else null
    }
}
