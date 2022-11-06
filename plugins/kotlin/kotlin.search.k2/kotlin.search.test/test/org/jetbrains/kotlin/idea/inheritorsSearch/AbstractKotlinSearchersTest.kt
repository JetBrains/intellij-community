// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.inheritorsSearch

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.psi.ElementDescriptionUtil
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import com.intellij.usageView.UsageViewLongNameLocation
import com.intellij.util.Query
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFunction
import java.nio.file.Paths

abstract class AbstractKotlinSearchersTest : KotlinLightCodeInsightFixtureTestCase() {
    override fun isFirPlugin(): Boolean = true

    abstract fun searchClass(ktClass: KtClass): Query<PsiElement>
    abstract fun searchFunction(ktFunction: KtFunction): Query<PsiElement>
    abstract fun searchJavaClass(psiClass: PsiClass): Query<PsiElement>

    fun doTestKotlinClass(testFilePath: String) {
        myFixture.configureByFile(testFilePath)

        val ktClass = myFixture.elementAtCaret.parentOfType<KtClass>(withSelf = true)
            ?: error("No declaration found at caret")

        if (testFilePath.contains("withJava")) {
            myFixture.configureByFile(testFilePath.replace("kt", "java"))
        }

        val result = ProgressManager.getInstance().run(object : Task.WithResult<List<PsiElement>, RuntimeException>(myFixture.project, "", false) {
            override fun compute(indicator: ProgressIndicator): List<PsiElement> {
                return runReadAction { searchClass(ktClass).toList() }
            }
        })
        val actual = render(result)
        KotlinTestUtils.assertEqualsToSibling(Paths.get(testFilePath), ".result.kt", actual)
    }


    fun doTestKotlinFunction(testFilePath: String) {
        myFixture.configureByFile(testFilePath)

        val ktFunction = myFixture.elementAtCaret.parentOfType<KtFunction>(withSelf = true) 
            ?: error("No declaration found at caret")

        if (testFilePath.contains("withJava")) {
            myFixture.configureByFile(testFilePath.replace("kt", "java"))
        }

        val result = ProgressManager.getInstance().run(object : Task.WithResult<List<PsiElement>, RuntimeException>(myFixture.project, "", false) {
            override fun compute(indicator: ProgressIndicator): List<PsiElement> {
                return runReadAction { searchFunction(ktFunction).toList() }
            }
        })
        val actual = render(result)
        KotlinTestUtils.assertEqualsToSibling(Paths.get(testFilePath), ".result.kt", actual)
    }


    fun doTestJavaClass(testFilePath: String) {
        myFixture.configureByFile(testFilePath)
        myFixture.configureByFile(testFilePath.replace("\\.java", ".kt"))

        val psiClass = myFixture.elementAtCaret.parentOfType<PsiClass>(withSelf = true)
            ?: error("No declaration found at caret")

        val result = ProgressManager.getInstance().run(object : Task.WithResult<List<PsiElement>, RuntimeException>(myFixture.project, "", false) {
            override fun compute(indicator: ProgressIndicator): List<PsiElement> {
                return runReadAction { searchJavaClass(psiClass).toList() }
            }
        })
        val actual = render(result)
        KotlinTestUtils.assertEqualsToSibling(Paths.get(testFilePath), ".result.kt", actual)
    }

    private fun render(elements: Collection<PsiElement>): String = buildList {
        for (declaration in elements) {
          val name = ElementDescriptionUtil.getElementDescription(declaration, UsageViewLongNameLocation.INSTANCE)
          add(declaration::class.simpleName!! + ": " + name)
        }
    }.sorted().joinToString(separator = "\n")
}