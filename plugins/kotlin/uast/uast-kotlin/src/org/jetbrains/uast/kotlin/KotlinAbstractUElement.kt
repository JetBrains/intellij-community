// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.kotlin.asJava.LightClassUtil
import org.jetbrains.kotlin.asJava.elements.KtLightElement
import org.jetbrains.kotlin.asJava.elements.KtLightMethod
import org.jetbrains.kotlin.asJava.toLightGetter
import org.jetbrains.kotlin.asJava.toLightSetter
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.kdoc.psi.impl.KDocLink
import org.jetbrains.kotlin.kdoc.psi.impl.KDocSection
import org.jetbrains.kotlin.kdoc.psi.impl.KDocTag
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.isPropertyParameter
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.utils.KotlinExceptionWithAttachments
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import org.jetbrains.uast.*
import org.jetbrains.uast.kotlin.expressions.KotlinLocalFunctionULambdaExpression
import org.jetbrains.uast.kotlin.expressions.KotlinUElvisExpression
import org.jetbrains.uast.kotlin.internal.KotlinUElementWithComments
import org.jetbrains.uast.kotlin.psi.UastKotlinPsiParameter
import org.jetbrains.uast.kotlin.psi.UastKotlinPsiVariable

abstract class KotlinAbstractUElement(private val givenParent: UElement?) : KotlinUElementWithComments {

    final override val uastParent: UElement? by lz {
        givenParent ?: convertParent()
    }

    protected open fun convertParent(): UElement? {
        @Suppress("DEPRECATION")
        val psi = psi //TODO: `psi` is deprecated but it seems that it couldn't be simply replaced for this case
        var parent = psi?.parent ?: sourcePsi?.parent ?: psi?.containingFile

        if (psi is PsiMethod && psi !is KtLightMethod) { // handling of synthetic things not represented in lightclasses directly
            when (parent) {
                is KtClassBody -> {
                    val grandParent = parent.parent
                    doConvertParent(this, grandParent)?.let { return it }
                    parent = grandParent
                }
                is KtFile -> {
                    parent.toUElementOfType<UClass>()?.let { return it } // mutlifile facade class
                }
            }

        }

        if (psi is KtLightElement<*, *> && sourcePsi.safeAs<KtClassOrObject>()?.isLocal == true) {
            val originParent = psi.kotlinOrigin?.parent
            parent = when (originParent) {
                null -> parent
                is KtClassBody -> originParent.parent
                else -> originParent
            }
        }

        if (psi is KtAnnotationEntry) {
            val parentUnwrapped = KotlinConverter.unwrapElements(parent) ?: return null
            val target = psi.useSiteTarget?.getAnnotationUseSiteTarget()
            when (target) {
                AnnotationUseSiteTarget.PROPERTY_GETTER ->
                    parent = (parentUnwrapped as? KtProperty)?.getter
                             ?: (parentUnwrapped as? KtParameter)?.toLightGetter()
                             ?: parent

                AnnotationUseSiteTarget.PROPERTY_SETTER ->
                    parent = (parentUnwrapped as? KtProperty)?.setter
                             ?: (parentUnwrapped as? KtParameter)?.toLightSetter()
                             ?: parent
                AnnotationUseSiteTarget.FIELD ->
                    parent = (parentUnwrapped as? KtProperty)
                        ?: (parentUnwrapped as? KtParameter)
                            ?.takeIf { it.isPropertyParameter() }
                            ?.let(LightClassUtil::getLightClassBackingField)
                                ?: parent
                AnnotationUseSiteTarget.SETTER_PARAMETER ->
                    parent = (parentUnwrapped as? KtParameter)
                        ?.toLightSetter()?.parameterList?.parameters?.firstOrNull() ?: parent
            }
        }
        if ((psi is UastKotlinPsiVariable || psi is UastKotlinPsiParameter) && parent != null) {
            parent = parent.parent
        }

        if (KotlinConverter.forceUInjectionHost) {
            if (parent is KtBlockStringTemplateEntry) {
                parent = parent.parent
            }
        } else
            while (parent is KtStringTemplateEntryWithExpression || parent is KtStringTemplateExpression && parent.entries.size == 1) {
                parent = parent.parent
            }

        if (parent is KtWhenConditionWithExpression) {
            parent = parent.parent
        }

        if (parent is KtImportList) {
            parent = parent.parent
        }

        if (psi is KtFunctionLiteral && parent is KtLambdaExpression) {
            parent = parent.parent
        }

        if (parent is KtLambdaArgument) {
            parent = parent.parent
        }

        if (psi is KtSuperTypeCallEntry) {
            parent = parent?.parent
        }

        if (parent is KtPropertyDelegate) {
            parent = parent.parent
        }

        val result = doConvertParent(this, parent)
        if (result == this) {
            throw KotlinExceptionWithAttachments("Loop in parent structure when converting a $psi of type ${psi?.javaClass} with parent $parent of type ${parent?.javaClass}")
                .withAttachment("text", parent?.text)
                .withAttachment("result", result)
        }

        return result
    }

    override fun equals(other: Any?): Boolean {
        if (other !is UElement) {
            return false
        }

        return this.psi == other.psi
    }

    override fun hashCode() = psi?.hashCode() ?: 0
}

fun doConvertParent(element: UElement, parent: PsiElement?): UElement? {
    val parentUnwrapped = KotlinConverter.unwrapElements(parent) ?: return null
    if (parent is KtValueArgument && parentUnwrapped is KtAnnotationEntry) {
        return (KotlinUastLanguagePlugin().convertElementWithParent(parentUnwrapped, null) as? KotlinUAnnotation)
            ?.findAttributeValueExpression(parent)
    }

    if (parent is KtParameter) {
        val annotationClass = findAnnotationClassFromConstructorParameter(parent)
        if (annotationClass != null) {
            return annotationClass.methods.find { it.name == parent.name }
        }
    }

    if (parent is KtClassInitializer) {
        val containingClass = parent.containingClassOrObject
        if (containingClass != null) {
            val containingUClass = KotlinUastLanguagePlugin().convertElementWithParent(containingClass, null) as? KotlinUClass
            containingUClass?.methods?.filterIsInstance<KotlinConstructorUMethod>()?.firstOrNull { it.isPrimary }?.let {
                return it.uastBody
            }
        }
    }

    val result = KotlinUastLanguagePlugin().convertElementWithParent(parentUnwrapped, null)

    if (result is KotlinUBlockExpression && element is UClass) {
        return KotlinUDeclarationsExpression(result).apply {
            declarations = listOf(element)
        }
    }

    if (result is UEnumConstant && element is UDeclaration) {
        return result.initializingClass
    }

    if (result is UCallExpression && result.uastParent is UEnumConstant) {
        return result.uastParent
    }

    if (result is USwitchClauseExpressionWithBody && !isInConditionBranch(element, result)) {
        val uYieldExpression = result.body.expressions.lastOrNull().safeAs<UYieldExpression>()
        if (uYieldExpression != null && uYieldExpression.expression == element)
            return uYieldExpression

        return result.body
    }

    if (result is KotlinUDestructuringDeclarationExpression &&
        when (parent) {
            is KtDestructuringDeclaration -> parent.initializer?.let { it == element.psi } == true
            is KtDeclarationModifierList -> parent == element.sourcePsi?.parent
            else -> false
        }
    ) {
        return result.tempVarAssignment
    }

    if (result is KotlinUElvisExpression && parentUnwrapped is KtBinaryExpression) {
        val branch: Sequence<PsiElement?> = element.psi?.parentsWithSelf.orEmpty().takeWhile { it != parentUnwrapped }
        if (branch.contains(parentUnwrapped.left))
            return result.lhsDeclaration
        if (branch.contains(parentUnwrapped.right))
            return result.rhsIfExpression
    }

    if ((result is UMethod || result is KotlinLocalFunctionULambdaExpression)
        && result !is KotlinConstructorUMethod // no sense to wrap super calls with `return`
        && element is UExpression
        && element !is UBlockExpression
        && element !is UTypeReferenceExpression // when element is a type in extension methods
    ) {
        return KotlinUBlockExpression.KotlinLazyUBlockExpression(result) { block ->
          listOf(KotlinUImplicitReturnExpression(block).apply { returnExpression = element })
        }.expressions.single()
    }

    if (result is KotlinULambdaExpression.Body && element is UExpression && result.implicitReturn?.returnExpression == element) {
        return result.implicitReturn!!
    }

    return result
}

private fun isInConditionBranch(element: UElement, result: USwitchClauseExpressionWithBody) =
        element.psi?.parentsWithSelf?.takeWhile { it !== result.psi }?.any { it is KtWhenCondition } ?: false


private fun findAnnotationClassFromConstructorParameter(parameter: KtParameter): UClass? {
    val primaryConstructor = parameter.getStrictParentOfType<KtPrimaryConstructor>() ?: return null
    val containingClass = primaryConstructor.getContainingClassOrObject()
    if (containingClass.isAnnotation()) {
        return KotlinUastLanguagePlugin().convertElementWithParent(containingClass, null) as? UClass
    }
    return null
}

abstract class KotlinAbstractUExpression(givenParent: UElement?) :
    KotlinAbstractUElement(givenParent),
    UExpression {

    override val javaPsi: PsiElement? = null

    override val psi
        get() = sourcePsi

    override val uAnnotations: List<UAnnotation>
        get() {
            val annotatedExpression = sourcePsi?.parent as? KtAnnotatedExpression ?: return emptyList()
            return annotatedExpression.annotationEntries.map { KotlinUAnnotation(it, this) }
        }
}

