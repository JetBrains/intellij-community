// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.inheritorsSearch

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.psi.ElementDescriptionUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.search.searches.DefinitionsScopedSearch
import com.intellij.psi.util.parentOfType
import com.intellij.usageView.UsageViewLongNameLocation
import org.jetbrains.kotlin.idea.test.KotlinLightMultiplatformCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.idea.test.configureMultiPlatformModuleStructure
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtClassOrObject
import java.nio.file.Paths

abstract class AbstractKotlinDefinitionsSearcherMultiplatformTest : KotlinLightMultiplatformCodeInsightFixtureTestCase() {

    fun doTestKotlinClass(path: String) {
        val virtualFile = myFixture.configureMultiPlatformModuleStructure(path).mainFile
        require(virtualFile != null)

        myFixture.configureFromExistingVirtualFile(virtualFile)
        val element = myFixture.getFile().findElementAt(myFixture.editor.caretModel.offset)
        assertNotNull("Can't find element at caret in file: $path", element)

        val ktClass = myFixture.elementAtCaret.parentOfType<KtClassOrObject>(withSelf = true)
            ?: error("No declaration found at caret")

        val result = ProgressManager.getInstance().run(object : Task.WithResult<List<PsiElement>, RuntimeException>(myFixture.project, "", false) {
            override fun compute(indicator: ProgressIndicator): List<PsiElement> {
                return DefinitionsScopedSearch.search(ktClass).asIterable().toList()
            }
        })
        val actual = render(result)
        KotlinTestUtils.assertEqualsToSibling(Paths.get(path), ".result.kt", actual)
    }


    fun doTestCallable(path: String) {
        val virtualFile = myFixture.configureMultiPlatformModuleStructure(path).mainFile
        require(virtualFile != null)

        myFixture.configureFromExistingVirtualFile(virtualFile)
        val element = myFixture.getFile().findElementAt(myFixture.editor.caretModel.offset)
        assertNotNull("Can't find element at caret in file: $path", element)

        val ktFunction = myFixture.elementAtCaret.parentOfType<KtCallableDeclaration>(withSelf = true)
            ?: error("No declaration found at caret")

        if (path.contains("withJava")) {
            myFixture.configureByFile(path.replace("kt", "java"))
        }

        val result = ProgressManager.getInstance().run(object : Task.WithResult<List<PsiElement>, RuntimeException>(myFixture.project, "", false) {
            override fun compute(indicator: ProgressIndicator): List<PsiElement> {
                return DefinitionsScopedSearch.search(ktFunction).asIterable().toList()
            }
        })
        val actual = render(result)
        KotlinTestUtils.assertEqualsToSibling(Paths.get(path), ".result.kt", actual)
    }

    protected fun render(elements: Collection<PsiElement>): String = buildList {
        for (declaration in elements) {
          val name = ElementDescriptionUtil.getElementDescription(declaration, UsageViewLongNameLocation.INSTANCE)
          add(declaration::class.simpleName!! + ": " + name)
        }
    }.sorted().joinToString(separator = "\n")
}