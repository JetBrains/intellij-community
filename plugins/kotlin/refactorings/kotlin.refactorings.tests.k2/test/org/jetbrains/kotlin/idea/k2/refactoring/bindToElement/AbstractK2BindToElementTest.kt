// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.bindToElement

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.stubindex.KotlinTopLevelFunctionFqnNameIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinTopLevelPropertyFqnNameIndex
import org.jetbrains.kotlin.idea.test.Directives
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.test.KotlinMultiFileLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.ProjectDescriptorWithStdlibSources
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.kdoc.psi.impl.KDocName
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtSimpleNameExpression

abstract class AbstractK2BindToElementTest : AbstractK2BindToTest() {
    override fun bindElement(refElement: KtElement) {
        val mainReference = refElement.mainReference ?: error("Ref element doesn't have a main reference")
        val bindTarget = findElementToBind()?.unwrapped ?: error("Could not find element to bind")
        myFixture.project.executeWriteCommand("bindToElement") {
            mainReference.bindToElement(bindTarget)
        }
        myFixture.checkResultByFile("${dataFile().name}.after")
    }

    private fun findElementToBind(): PsiElement? {
        val nameToBind = InTextDirectivesUtils.findStringWithPrefixes(file.text, BIND_TO) ?: return null
        val projectScope = GlobalSearchScope.allScope(myFixture.project)
        return JavaPsiFacade.getInstance(myFixture.project).findClass(nameToBind, projectScope)
               ?: KotlinTopLevelFunctionFqnNameIndex[nameToBind, myFixture.project, projectScope].firstOrNull()
               ?: KotlinTopLevelPropertyFqnNameIndex[nameToBind, myFixture.project, projectScope].firstOrNull()
               ?: JavaPsiFacade.getInstance(myFixture.project).findClass(nameToBind.substringBeforeLast("."), projectScope)?.findMethodsByName(nameToBind.substringAfterLast("."))?.get(0) as PsiMethod
    }
}