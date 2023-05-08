// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.uast.kotlin

import com.intellij.lang.Language
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.project.structure.KtNotUnderContentRootModule
import org.jetbrains.kotlin.analysis.project.structure.getKtModule
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.uast.DEFAULT_TYPES_LIST
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UastLanguagePlugin
import org.jetbrains.uast.kotlin.FirKotlinConverter.convertDeclarationOrElement
import org.jetbrains.uast.kotlin.psi.UastFakeSourceLightPrimaryConstructor
import org.jetbrains.uast.util.ClassSet
import org.jetbrains.uast.util.ClassSetsWrapper

class FirKotlinUastLanguagePlugin : UastLanguagePlugin {
    override val priority: Int = 10

    override val language: Language
        get() = KotlinLanguage.INSTANCE

    override fun isFileSupported(fileName: String): Boolean {
        return when {
            fileName.endsWith(".kt", false) -> true
            fileName.endsWith(".kts", false) -> Registry.`is`("kotlin.k2.scripting.enabled", false)
            else -> false
        }
    }

    private val PsiElement.isJvmElement: Boolean
        get() {
            val resolveProvider = ServiceManager.getService(project, FirKotlinUastResolveProviderService::class.java)
            return resolveProvider.isJvmElement(this)
        }

    private val PsiElement.isSupportedElement: Boolean
        get() {
            if (!isJvmElement) {
                return false
            }

            val containingFile = containingFile?.let(::unwrapFakeFileForLightClass) as? KtFile ?: return false

            // `getKtModule` can be slow (KTIJ-25470). Since most files will be in a module or library, we can optimize this hot path using
            // `ProjectFileIndex`.
            val virtualFile = containingFile.virtualFile
            if (virtualFile != null) {
                val fileIndex = ProjectRootManager.getInstance(containingFile.project).fileIndex
                if (fileIndex.isInSourceContent(virtualFile) || fileIndex.isInLibrary(virtualFile)) {
                    return true
                }
            }

            // The checks above might not work in all possible situations (e.g. scripts) and `getKtModule` is able to give a definitive
            // answer.
            return containingFile.getKtModule(project) !is KtNotUnderContentRootModule
        }

    override fun convertElement(element: PsiElement, parent: UElement?, requiredType: Class<out UElement>?): UElement? {
        if (!element.isSupportedElement) return null
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

    override fun getConstructorCallExpression(
        element: PsiElement,
        fqName: String
    ): UastLanguagePlugin.ResolvedConstructor? {
        TODO("Not yet implemented")
    }

    override fun getMethodCallExpression(
        element: PsiElement,
        containingClassFqName: String?,
        methodName: String
    ): UastLanguagePlugin.ResolvedMethod? {
        TODO("Not yet implemented")
    }

    override fun isExpressionValueUsed(element: UExpression): Boolean {
        TODO("Not yet implemented")
    }
}
