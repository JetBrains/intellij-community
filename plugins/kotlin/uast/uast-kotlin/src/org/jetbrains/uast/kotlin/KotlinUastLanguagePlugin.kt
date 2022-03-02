// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import com.intellij.lang.Language
import com.intellij.psi.*
import org.jetbrains.kotlin.asJava.classes.KtLightClassForFacade
import org.jetbrains.kotlin.descriptors.ConstructorDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.uast.*
import org.jetbrains.uast.analysis.UastAnalysisPlugin
import org.jetbrains.uast.kotlin.KotlinConverter.convertDeclaration
import org.jetbrains.uast.kotlin.KotlinConverter.convertDeclarationOrElement
import org.jetbrains.uast.kotlin.psi.UastFakeLightPrimaryConstructor
import org.jetbrains.uast.util.ClassSet
import org.jetbrains.uast.util.ClassSetsWrapper

class KotlinUastLanguagePlugin : UastLanguagePlugin {
    override val priority = 10

    override val language: Language
        get() = KotlinLanguage.INSTANCE

    override fun isFileSupported(fileName: String): Boolean {
        return fileName.endsWith(".kt", false) || fileName.endsWith(".kts", false)
    }

    private val PsiElement.isJvmElement: Boolean
        get() {
            val resolveProvider: KotlinUastResolveProviderService = project.getService(KotlinUastResolveProviderService::class.java)!!
            return resolveProvider.isJvmElement(this)
        }

    override fun convertElement(element: PsiElement, parent: UElement?, requiredType: Class<out UElement>?): UElement? {
        val requiredTypes = elementTypes(requiredType)
        return if (!canConvert(element, requiredTypes) || !element.isJvmElement) null
        else convertDeclarationOrElement(element, parent, requiredTypes)
    }

    override fun convertElementWithParent(element: PsiElement, requiredType: Class<out UElement>?): UElement? {
        val requiredTypes = elementTypes(requiredType)
        return when {
            !canConvert(element, requiredTypes) || !element.isJvmElement -> null
            element is PsiFile || element is KtLightClassForFacade -> convertDeclaration(element, null, requiredTypes)
            else -> convertDeclarationOrElement(element, null, requiredTypes)
        }
    }

    override fun getMethodCallExpression(
        element: PsiElement,
        containingClassFqName: String?,
        methodName: String
    ): UastLanguagePlugin.ResolvedMethod? {
        if (element !is KtCallExpression) return null
        val resolvedCall = element.getResolvedCall(element.analyze()) ?: return null
        val resultingDescriptor = resolvedCall.resultingDescriptor
        if (resultingDescriptor !is FunctionDescriptor || resultingDescriptor.name.asString() != methodName) return null

        val parent = element.parent
        val parentUElement = convertElementWithParent(parent, null) ?: return null

        val uExpression = KotlinUFunctionCallExpression(element, parentUElement)
        val method = uExpression.resolve() ?: return null
        if (method.name != methodName) return null
        return UastLanguagePlugin.ResolvedMethod(uExpression, method)
    }

    override fun getConstructorCallExpression(
        element: PsiElement,
        fqName: String
    ): UastLanguagePlugin.ResolvedConstructor? {
        if (element !is KtCallExpression) return null
        val resolvedCall = element.getResolvedCall(element.analyze()) ?: return null
        val resultingDescriptor = resolvedCall.resultingDescriptor
        if (resultingDescriptor !is ConstructorDescriptor
            || resultingDescriptor.returnType.constructor.declarationDescriptor?.name?.asString() != fqName
        ) {
            return null
        }

        val parent = KotlinConverter.unwrapElements(element.parent) ?: return null
        val parentUElement = convertElementWithParent(parent, null) ?: return null

        val uExpression = KotlinUFunctionCallExpression(element, parentUElement)
        val method = uExpression.resolve() ?: return null
        val containingClass = method.containingClass ?: return null
        return UastLanguagePlugin.ResolvedConstructor(uExpression, method, containingClass)
    }

    override fun isExpressionValueUsed(element: UExpression): Boolean {
        return when (element) {
            is KotlinUSimpleReferenceExpression.KotlinAccessorCallExpression -> element.setterValue != null
            is KotlinAbstractUExpression -> {
                val ktElement = element.sourcePsi as? KtElement ?: return false
                ktElement.analyze()[BindingContext.USED_AS_EXPRESSION, ktElement] ?: false
            }
            else -> false
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : UElement> convertElement(element: PsiElement, parent: UElement?, expectedTypes: Array<out Class<out T>>): T? {
        val nonEmptyExpectedTypes = expectedTypes.nonEmptyOr(DEFAULT_TYPES_LIST)
        return if (!canConvert(element, nonEmptyExpectedTypes) || !element.isJvmElement) null
        else convertDeclarationOrElement(element, parent, nonEmptyExpectedTypes) as? T
    }

    override fun <T : UElement> convertElementWithParent(element: PsiElement, requiredTypes: Array<out Class<out T>>): T? {
        return convertElement(element, null, requiredTypes)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : UElement> convertToAlternatives(element: PsiElement, requiredTypes: Array<out Class<out T>>): Sequence<T> =
        if (!element.isJvmElement) emptySequence() else when {
            element is KtFile -> KotlinConverter.convertKtFile(element, null, requiredTypes) as Sequence<T>
            (element is KtProperty && !element.isLocal) ->
                KotlinConverter.convertNonLocalProperty(element, null, requiredTypes) as Sequence<T>
            element is KtParameter -> KotlinConverter.convertParameter(element, null, requiredTypes) as Sequence<T>
            element is KtClassOrObject -> KotlinConverter.convertClassOrObject(element, null, requiredTypes) as Sequence<T>
            element is UastFakeLightPrimaryConstructor ->
                KotlinConverter.convertFakeLightConstructorAlternatives(element, null, requiredTypes) as Sequence<T>
            else -> sequenceOf(convertElementWithParent(element, requiredTypes.nonEmptyOr(DEFAULT_TYPES_LIST)) as? T).filterNotNull()
        }

    override fun getPossiblePsiSourceTypes(vararg uastTypes: Class<out UElement>): ClassSet<PsiElement> =
        when (uastTypes.size) {
            0 -> getPossibleSourceTypes(UElement::class.java)
            1 -> getPossibleSourceTypes(uastTypes.single())
            else -> ClassSetsWrapper<PsiElement>(Array(uastTypes.size) { getPossibleSourceTypes(uastTypes[it]) })
        }

    override val analysisPlugin: UastAnalysisPlugin?
        get() = UastAnalysisPlugin.byLanguage(KotlinLanguage.INSTANCE)
}
