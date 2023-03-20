// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.uast.kotlin

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.kotlin.asJava.LightClassUtil
import org.jetbrains.kotlin.asJava.elements.KtLightElement
import org.jetbrains.kotlin.asJava.elements.KtLightMethod
import org.jetbrains.kotlin.asJava.toLightGetter
import org.jetbrains.kotlin.asJava.toLightSetter
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.isPropertyParameter
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.utils.KotlinExceptionWithAttachments
import org.jetbrains.uast.*
import org.jetbrains.uast.kotlin.psi.UastKotlinPsiParameter
import org.jetbrains.uast.kotlin.psi.UastKotlinPsiVariable

internal fun convertParentImpl(
    service: BaseKotlinUastResolveProviderService,
    uElement: UElement
): UElement? {
    @Suppress("DEPRECATION")
    val psi = uElement.psi //TODO: `psi` is deprecated but it seems that it couldn't be simply replaced for this case
    var parent = psi?.parent ?: uElement.sourcePsi?.parent ?: psi?.containingFile

    if (psi is PsiMethod && psi !is KtLightMethod) { // handling of synthetic things not represented in lightclasses directly
        when (parent) {
            is KtClassBody -> {
                val grandParent = parent.parent
                convertParentImpl(service, uElement, grandParent)?.let { return it }
                parent = grandParent
            }

            is KtFile -> {
                parent.toUElementOfType<UClass>()?.let { return it } // mutlifile facade class
            }
        }

    }

    if (psi is KtLightElement<*, *> && (uElement.sourcePsi as? KtClassOrObject)?.isLocal == true) {
        val originParent = psi.kotlinOrigin?.parent
        parent = when (originParent) {
            null -> parent
            is KtClassBody -> originParent.parent
            else -> originParent
        }
    }

    if (psi is KtAnnotationEntry) {
        val parentUnwrapped = service.baseKotlinConverter.unwrapElements(parent) ?: return null
        when (psi.useSiteTarget?.getAnnotationUseSiteTarget()) {
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

            else -> {}
        }
    }
    if ((psi is UastKotlinPsiVariable || psi is UastKotlinPsiParameter) && parent != null) {
        parent = parent.parent
    }

    if (service.baseKotlinConverter.forceUInjectionHost()) {
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
    
    if (parent is KtParameter && parent.ownerFunction == null) {
        parent = parent.parent
    }

    if (parent is KtUserType &&  parent.parent.parent is KtConstructorCalleeExpression) {
        parent =  parent.parent.parent.parent
    } 
    
    if (psi is KtSuperTypeCallEntry) {
        parent = parent?.parent
    }

    if (parent is KtPropertyDelegate) {
        parent = parent.parent
    }

    val result = convertParentImpl(service, uElement, parent)
    if (result == uElement) {
        throw KotlinExceptionWithAttachments("Loop in parent structure when converting a $psi of type ${psi?.javaClass} with parent $parent of type ${parent?.javaClass}")
            .withAttachment("text", parent?.text)
            .withAttachment("result", result)
    }

    return result
}

internal fun convertParentImpl(
    service: BaseKotlinUastResolveProviderService,
    element: UElement,
    parent: PsiElement?
): UElement? {
    val parentUnwrapped = service.baseKotlinConverter.unwrapElements(parent) ?: return null
    if (parent is KtValueArgument && parentUnwrapped is KtAnnotationEntry) {
        return (service.languagePlugin.convertElementWithParent(parentUnwrapped, null) as? KotlinUAnnotation)?.let {
            service.findAttributeValueExpression(it, parent)
        }
    }

    if (parent is KtParameter) {
        val annotationClass = findAnnotationClassFromConstructorParameter(service.languagePlugin, parent)
        if (annotationClass != null) {
            return annotationClass.methods.find { it.name == parent.name }
        }
    }

    if (parent is KtClassInitializer) {
        val containingClass = parent.containingClassOrObject
        if (containingClass != null) {
            val containingUClass = service.languagePlugin.convertElementWithParent(containingClass, null) as? KotlinUClass
            containingUClass?.methods?.filterIsInstance<KotlinConstructorUMethod>()?.firstOrNull { it.isPrimary }?.let {
                return it.uastBody
            }
        }
    }

    val result = service.languagePlugin.convertElementWithParent(parentUnwrapped, null)

    if (result is KotlinUBlockExpression && element is UClass) {
        return KotlinUDeclarationsExpression(result).apply {
            declarations = listOf(element)
        }
    }

    if (result is UEnumConstant && element is UDeclaration) {
        return result.initializingClass
    }

    if (element !is KotlinUAnonymousClass && result is KotlinUObjectLiteralExpression) {
        result.constructorCall?.let { return it }
    }

    if (result is UCallExpression && result.uastParent is UEnumConstant) {
        return result.uastParent
    }

    if (result is USwitchClauseExpressionWithBody && !isInConditionBranch(element, result)) {
        val uYieldExpression = result.body.expressions.lastOrNull() as? UYieldExpression
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
        return KotlinLazyUBlockExpression(result) { block ->
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

private fun findAnnotationClassFromConstructorParameter(
    languagePlugin: UastLanguagePlugin,
    parameter: KtParameter
): UClass? {
    val primaryConstructor = parameter.getStrictParentOfType<KtPrimaryConstructor>() ?: return null
    val containingClass = primaryConstructor.getContainingClassOrObject()
    if (containingClass.isAnnotation()) {
        return languagePlugin.convertElementWithParent(containingClass, null) as? UClass
    }
    return null
}

/**
 * Return the immediate parent of the given type
 *
 * Unlike [PsiElement#getParentOfType] variants that find the closest parent of the type (may include itself),
 * this util is looking for the immediate parent only, hence a drop-in replacement of `parent.safeAs<T>()`.
 * Note that `safeAs` becomes error-level opt-in due to the unsafe cast in certain platforms.
 */
@Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
inline fun <reified T : PsiElement> PsiElement?.parentAs() : @kotlin.internal.NoInfer T? = this?.parent as? T

/**
 * Returns parent [KtObjectLiteralExpression] for the given [KtSuperTypeCallEntry]
 *
 * E.g., `object : MyInterface { ... }`
 * this returns [KtObjectLiteralExpression] for that `object` from [KtSuperTypeCallEntry] of `MyInterface`
 */
internal fun KtSuperTypeCallEntry.getParentObjectLiteralExpression(): KtObjectLiteralExpression? {
    return parentAs<KtSuperTypeList>().parentAs<KtObjectDeclaration>().parentAs<KtObjectLiteralExpression>()
}
