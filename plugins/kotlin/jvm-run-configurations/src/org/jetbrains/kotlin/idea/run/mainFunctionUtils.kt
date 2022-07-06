/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.run

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.asJava.elements.KtLightMethod
import org.jetbrains.kotlin.idea.base.lineMarkers.run.KotlinMainFunctionDetector
import org.jetbrains.kotlin.idea.base.projectStructure.RootKindFilter
import org.jetbrains.kotlin.idea.base.projectStructure.matches
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

private fun getMainFunCandidates(psiClass: PsiClass): Collection<KtNamedFunction> {
    return psiClass.allMethods.map { method: PsiMethod ->
        if (method !is KtLightMethod) return@map null
        if (method.getName() != "main") return@map null
        val declaration =
            method.kotlinOrigin
        if (declaration is KtNamedFunction) declaration else null
    }.filterNotNull()
}

internal fun findMainInClass(psiClass: PsiClass): KtNamedFunction? {
    val mainLocatingService = KotlinMainFunctionDetector.getInstance()

    return getMainFunCandidates(psiClass).find { mainLocatingService.isMain(it) }
}

/**
 * Allows to find the nearest container (i.e. a class, an object or a file) which
 * has a 'main' function.
 *
 * Uses [KotlinMainFunctionDetector] to detect the main.
 */
object EntryPointContainerFinder {
    fun find(locationElement: PsiElement): KtDeclarationContainer? {

        val psiFile = locationElement.containingFile
        if (!(psiFile is KtFile && RootKindFilter.projectAndLibrarySources.matches(psiFile))) return null

        val mainLocatingService = KotlinMainFunctionDetector.getInstance()

        var currentElement = locationElement.declarationContainer(false)
        // look up outside: from the inner declaration to the most top level declarations
        while (currentElement != null) {
            var entryPointContainer = currentElement
            if (entryPointContainer is KtClass) {
                entryPointContainer = entryPointContainer.companionObjects.singleOrNull()
            }
            if (entryPointContainer != null && mainLocatingService.hasMain(entryPointContainer.declarations)) return entryPointContainer
            currentElement = (currentElement as PsiElement).declarationContainer(true)
        }

        return locationElement.safeAs<KtFile>()?.let(::findInFile)
    }

    private fun findInFile(locationElement: KtFile): KtDeclarationContainer? {
        val mainLocatingService = KotlinMainFunctionDetector.getInstance()
        // look up inside declarations
        fun KtClassOrObject.lookupDeepFirstMainDeclarations(): KtDeclarationContainer? {
            if (this.isLocal) return null

            if (mainLocatingService.hasMain(declarations)) return this

            for (declaration in declarations) {
                declaration.safeAs<KtClassOrObject>()?.lookupDeepFirstMainDeclarations()?.let { return it }
            }
            return null
        }

        val declarations = locationElement.declarations
        for (declaration in declarations) {
            val classOrObject = declaration as? KtClassOrObject ?: continue
            classOrObject.lookupDeepFirstMainDeclarations()?.let { return it }
        }

        return null
    }

    private fun PsiElement.declarationContainer(strict: Boolean): KtDeclarationContainer? {
        val element = if (strict)
            PsiTreeUtil.getParentOfType(this, KtClassOrObject::class.java, KtFile::class.java)
        else
            PsiTreeUtil.getNonStrictParentOfType(this, KtClassOrObject::class.java, KtFile::class.java)
        return element
    }
}
