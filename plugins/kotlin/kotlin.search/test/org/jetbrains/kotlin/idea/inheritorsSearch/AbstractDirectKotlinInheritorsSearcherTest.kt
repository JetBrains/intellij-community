// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.inheritorsSearch

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.psi.ElementDescriptionUtil
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.util.parentOfType
import com.intellij.usageView.UsageViewLongNameLocation
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KtClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.nameOrAnonymous
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.utils.keysToMap
import java.nio.file.Paths

abstract class AbstractDirectKotlinInheritorsSearcherTest : KotlinLightCodeInsightFixtureTestCase() {
    override fun isFirPlugin(): Boolean = true

    fun doTestKotlinClass(testFilePath: String) {
        myFixture.configureByFile(testFilePath)

        val ktClass = myFixture.elementAtCaret.parentOfType<KtClass>(withSelf = true) 
            ?: error("No declaration found at caret")

        if (testFilePath.contains("withJava")) {
            myFixture.configureByFile(testFilePath.replace("kt", "java"))
        }

        val result = ProgressManager.getInstance().run(object : Task.WithResult<Map<KtClassOrObjectSymbol, String>, RuntimeException>(myFixture.project, "", false) {
            override fun compute(indicator: ProgressIndicator): Map<KtClassOrObjectSymbol, String> {
                return runReadAction { analyze(ktClass) { DirectKotlinClassInheritorsSearch.search(ktClass).keysToMap { it.nameOrAnonymous.asString() } } }
            }
        })
        val actual = render(result.keys) { result[it]!!}
        KotlinTestUtils.assertEqualsToSibling(Paths.get(testFilePath), ".result.kt", actual)
    }
    
    fun doTestJavaClass(testFilePath: String) {
        myFixture.configureByFile(testFilePath)
        myFixture.configureByFile(testFilePath.replace("\\.java", ".kt"))

        val psiClass = myFixture.elementAtCaret.parentOfType<PsiClass>(withSelf = true)
            ?: error("No declaration found at caret")

        val result = ProgressManager.getInstance().run(object : Task.WithResult<List<PsiElement>, RuntimeException>(myFixture.project, "", false) {
            override fun compute(indicator: ProgressIndicator): List<PsiElement> {
                return runReadAction { ClassInheritorsSearch.search(psiClass, false).toList() }
            }
        })
        val actual = render(result) { ElementDescriptionUtil.getElementDescription(it, UsageViewLongNameLocation.INSTANCE) }
        KotlinTestUtils.assertEqualsToSibling(Paths.get(testFilePath), ".result.kt", actual)
    }

    private fun <T> render(elements: Collection<T>, toStr : (T) -> String): String = buildList {
        for (declaration in elements) {
          val name = toStr(declaration)
          add(declaration!!::class.simpleName!! + ": " + name)
        }
    }.sorted().joinToString(separator = "\n")
}

class A : B() //mod1 -> mod2
open class B //mod2