// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.uast.kotlin

import com.intellij.lang.Language
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.resolution.singleConstructorCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.uast.*
import org.jetbrains.uast.analysis.UastAnalysisPlugin
import org.jetbrains.uast.kotlin.FirKotlinConverter.convertDeclarationOrElement
import org.jetbrains.uast.kotlin.psi.UastFakeSourceLightPrimaryConstructor
import org.jetbrains.uast.util.ClassSet
import org.jetbrains.uast.util.ClassSetsWrapper

private val JVM_STATIC_FQN = FqName("kotlin.jvm.JvmStatic")

class FirKotlinUastLanguagePlugin : UastLanguagePlugin {
    override val priority: Int = 10

    override val language: Language
        get() = KotlinLanguage.INSTANCE

    override val analysisPlugin: UastAnalysisPlugin?
        get() = UastAnalysisPlugin.byLanguage(KotlinLanguage.INSTANCE)

    override fun isFileSupported(fileName: String): Boolean = when {
        fileName.endsWith(".kt", false) -> true
        fileName.endsWith(".kts", false) -> true
        else -> false
    }

    private val PsiElement.isSupportedElement: Boolean
        get() {
            val ktFile = containingFile?.let(::unwrapFakeFileForLightClass) as? KtFile ?: return false
            return ApplicationManager.getApplication().service<FirKotlinUastResolveProviderService>().isSupportedFile(ktFile)
        }

    override fun convertElement(element: PsiElement, parent: UElement?, requiredType: Class<out UElement>?): UElement? {
        if (parent == null && !element.isSupportedElement) return null
        return convertDeclarationOrElement(element, parent, elementTypes(requiredType))
    }

    override fun convertElementWithParent(element: PsiElement, requiredType: Class<out UElement>?): UElement? {
        if (!element.isSupportedElement) return null
        return convertDeclarationOrElement(element, null, elementTypes(requiredType))
    }

    override fun getPossiblePsiSourceTypes(vararg uastTypes: Class<out UElement>): ClassSet<PsiElement> =
        when (uastTypes.size) {
            0 -> getPossibleSourceTypes(UElement::class.java)
            1 -> getPossibleSourceTypes(uastTypes.single())
            else -> ClassSetsWrapper(Array(uastTypes.size) { getPossibleSourceTypes(uastTypes[it]) })
        }

    @Suppress("UNCHECKED_CAST")
    override fun <T : UElement> convertElementWithParent(element: PsiElement, requiredTypes: Array<out Class<out T>>): T? {
        if (!element.isSupportedElement) return null
        val nonEmptyRequiredTypes = requiredTypes.nonEmptyOr(DEFAULT_TYPES_LIST)
        return convertDeclarationOrElement(element, null, nonEmptyRequiredTypes) as? T
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : UElement> convertToAlternatives(element: PsiElement, requiredTypes: Array<out Class<out T>>): Sequence<T> {
        if (!element.isSupportedElement) return emptySequence()
        return when {
            element is KtFile ->
                FirKotlinConverter.convertKtFile(element, null, requiredTypes) as Sequence<T>
            element is KtClassOrObject ->
                FirKotlinConverter.convertClassOrObject(element, null, requiredTypes) as Sequence<T>
            element is KtNamedFunction && element.isJvmStatic() ->
                FirKotlinConverter.convertJvmStaticMethod(element, null, requiredTypes) as Sequence<T>
            element is KtProperty && !element.isLocal ->
                FirKotlinConverter.convertNonLocalProperty(element, null, requiredTypes) as Sequence<T>
            element is KtParameter ->
                FirKotlinConverter.convertParameter(element, null, requiredTypes) as Sequence<T>
            element is UastFakeSourceLightPrimaryConstructor ->
                FirKotlinConverter.convertFakeLightConstructorAlternatives(element, null, requiredTypes) as Sequence<T>
            else ->
                sequenceOf(convertElementWithParent(element, requiredTypes.nonEmptyOr(DEFAULT_TYPES_LIST)) as? T).filterNotNull()
        }
    }

    @OptIn(KaAllowAnalysisOnEdt::class)
    private fun KtNamedFunction.isJvmStatic() = annotationEntries.any { annotation ->
        annotation.shortName?.asString() == JVM_STATIC_FQN.shortName().asString() && allowAnalysisOnEdt {
            analyze(annotation) {
                annotation.resolveToCall()
                    ?.singleConstructorCallOrNull()
                    ?.partiallyAppliedSymbol
                    ?.symbol
                    ?.containingClassId
                    ?.asSingleFqName() == JVM_STATIC_FQN
            }
        }
    }

    override fun getContainingAnnotationEntry(uElement: UElement?, annotationsHint: Collection<String>): Pair<UAnnotation, String?>? {
        val sourcePsi = uElement?.sourcePsi ?: return null

        val parent = sourcePsi.parent ?: return null
        if (parent is KtAnnotationEntry) {
            if (!isOneOfNames(parent, annotationsHint)) return null

            return super.getContainingAnnotationEntry(uElement, annotationsHint)
        }

        val annotationEntry = parent.getParentOfType<KtAnnotationEntry>(true, KtDeclaration::class.java)
        if (annotationEntry == null) return null

        if (!isOneOfNames(annotationEntry, annotationsHint)) return null

        return super.getContainingAnnotationEntry(uElement, annotationsHint)
    }

    private fun isOneOfNames(annotationEntry: KtAnnotationEntry, annotations: Collection<String>): Boolean {
        if (annotations.isEmpty()) return true
        val shortName = annotationEntry.shortName?.identifier ?: return false

        for (annotation in annotations) {
            if (StringUtil.getShortName(annotation) == shortName) {
                return true
            }
        }
        return false
    }

    override fun getConstructorCallExpression(
        element: PsiElement,
        fqName: String
    ): UastLanguagePlugin.ResolvedConstructor {
        TODO("Not yet implemented")
    }

    override fun getMethodCallExpression(
        element: PsiElement,
        containingClassFqName: String?,
        methodName: String
    ): UastLanguagePlugin.ResolvedMethod {
        TODO("Not yet implemented")
    }

    override fun isExpressionValueUsed(element: UExpression): Boolean {
        TODO("Not yet implemented")
    }
}
