// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.navigation

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.common.runAll
import org.jetbrains.kotlin.analysis.project.structure.KtLibrarySourceModule
import org.jetbrains.kotlin.analysis.project.structure.getKtModule
import org.jetbrains.kotlin.idea.fir.invalidateCaches
import org.jetbrains.kotlin.idea.resolve.AbstractReferenceResolveTest
import org.jetbrains.kotlin.idea.test.KotlinLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.getPrevSiblingIgnoringWhitespace

abstract class AbstractKotlinNavigationToLibrarySourceTest : AbstractReferenceResolveTest() {
    override fun isFirPlugin(): Boolean = true

    override fun getProjectDescriptor(): KotlinLightProjectDescriptor =
        KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstanceFullJdk()

    override fun performAdditionalResolveChecks(results: List<PsiElement>) {
        for (result in results) {
            val navigationElement = result.navigationElement
            val ktModule = navigationElement.getKtModule()
            UsefulTestCase.assertTrue(
                "reference should be resolved to the psi element from ${KtLibrarySourceModule::class} but was resolved to ${ktModule::class}",
                ktModule is KtLibrarySourceModule
            )
        }
    }

    override fun render(element: PsiElement): String {
        return when (val navigationElement = element.navigationElement) {
            is KtNamedDeclaration -> "(" + (navigationElement.fqName?.asString() ?: "<local>") + ") " + navigationElement.signatureText()
            else -> super.render(element)
        }
    }

    private fun KtNamedDeclaration.signatureText(): String {
        val firstElement = children.first { it !is PsiComment && it !is PsiWhiteSpace }
        val endOffset = when (this) {
            is KtNamedFunction -> typeReference ?: valueParameterList?.rightParenthesis
            is KtProperty -> typeReference ?: equalsToken?.getPrevSiblingIgnoringWhitespace()
            is KtClassOrObject -> primaryConstructor?.valueParameterList?.rightParenthesis
                ?: getColon()?.getPrevSiblingIgnoringWhitespace()
                ?: body?.getPrevSiblingIgnoringWhitespace()
            else -> lastChild
        }
        return containingFile.text.subSequence(firstElement.startOffset, endOffset?.endOffset ?: lastChild!!.endOffset).toString()
            .replace("\n", " ")
    }

    override fun tearDown() {
        runAll(
            { project.invalidateCaches() },
            { super.tearDown() }
        )
    }
}