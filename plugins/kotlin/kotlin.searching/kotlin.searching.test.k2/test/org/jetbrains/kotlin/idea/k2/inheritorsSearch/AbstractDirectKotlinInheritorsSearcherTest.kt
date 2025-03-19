// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.inheritorsSearch

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.search.searches.OverridingMethodsSearch
import com.intellij.psi.util.parentOfType
import com.intellij.util.Query
import org.jetbrains.kotlin.idea.searching.inheritors.DirectKotlinClassInheritorsSearch
import org.jetbrains.kotlin.idea.searching.inheritors.DirectKotlinOverridingCallableSearch
import org.jetbrains.kotlin.idea.searching.inheritors.findAllOverridings
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.test.InTextDirectivesUtils
import java.nio.file.Paths

abstract class AbstractDirectKotlinInheritorsSearcherTest : AbstractKotlinSearchersTest() {
    override fun searchClass(ktClass: KtClass): Query<out PsiElement> {
        return DirectKotlinClassInheritorsSearch.search(ktClass)
    }

    override fun searchCallable(ktFunction: KtCallableDeclaration): Query<out PsiElement> {
        return DirectKotlinOverridingCallableSearch.search(ktFunction)
    }

    override fun searchJavaClass(psiClass: PsiClass): Query<PsiElement> {
        return ClassInheritorsSearch.search(psiClass, false).mapping { it as PsiElement }
    }

    override fun searchJavaMethod(psiMethod: PsiMethod): Query<PsiElement> {
        //with only direct inheritance, the results are unstable:
        //KotlinOverridingMethodsWithFlexibleTypesSearcher does the search in addition to the normal JavaOverridingMethodsSearcher
        //thus it happens that sometimes it founds another inheritor first,
        // and then 2 results are found in one run and in another run we find only one
        return OverridingMethodsSearch.search(psiMethod).mapping { it as PsiElement }
    }

    /**
     * Use LOCAL_SCOPE directive to create LocalSearchScope from provided files.
     * This avoids caching in java searchers and makes failures more predictable.
     */
    fun doTestFindAllOverridings(testFilePath: String) {
        val file = myFixture.configureByFile(testFilePath)

        val ktFunction = myFixture.elementAtCaret.parentOfType<KtCallableDeclaration>(withSelf = true)
            ?: error("No declaration found at caret")


        val javaFile = if (testFilePath.contains("withJava")) {
            myFixture.configureByFile(testFilePath.replace("kt", "java"))
        }
        else {
            null
        }

        val scope = runReadAction {
            if (InTextDirectivesUtils.isDirectiveDefined(file.text, "LOCAL_SCOPE") && javaFile != null) LocalSearchScope(
                arrayOf(
                    file, javaFile
                )
            ) else ktFunction.useScope
        }

        val result =
            ProgressManager.getInstance().run(object : Task.WithResult<List<PsiElement>, RuntimeException>(myFixture.project, "", false) {
                override fun compute(indicator: ProgressIndicator): List<PsiElement> {
                    return ktFunction.findAllOverridings(scope).toList()
                }
            })
        val actual = render(result)
        KotlinTestUtils.assertEqualsToSibling(Paths.get(testFilePath), ".result.kt", actual)
    }
}