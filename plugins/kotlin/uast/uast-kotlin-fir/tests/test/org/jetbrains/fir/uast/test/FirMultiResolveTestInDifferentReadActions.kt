// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.fir.uast.test

import com.intellij.openapi.application.runReadAction
import com.intellij.platform.uast.testFramework.env.findElementByTextFromPsi
import com.intellij.refactoring.suggested.startOffset
import junit.framework.TestCase
import org.jetbrains.kotlin.asJava.elements.KtLightMethod
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UMultiResolvable
import org.jetbrains.uast.UastFacade
import org.jetbrains.uast.test.common.kotlin.UastPluginSelection
import org.jetbrains.uast.test.common.kotlin.orFail

class FirMultiResolveTestInDifferentReadActions : KotlinLightCodeInsightFixtureTestCase(), UastPluginSelection {
    override val isFirUastPlugin: Boolean = true
    override fun isFirPlugin(): Boolean = true

    // see https://youtrack.jetbrains.com/issue/KT-60539
    fun testMultiResolveFromDifferentReadAction(){
        myFixture.configureByText(
            "test.kt", """
            fun foo(a: String, b: Int) {}
            fun foo(a: Int) {}
            
            fun bar() {
                foo()
            }
            """.trimIndent()
        )
        val uFile = UastFacade.convertElementWithParent(myFixture.file, requiredType = null)!!

        val foo = uFile.findElementByTextFromPsi<UCallExpression>("foo()", strict = false)
            .orFail("can't find call `foo`")

        val multiResolveIterable = runReadAction{ (foo as UMultiResolvable).multiResolve() }
        myFixture.project.invalidateAllCachesForUastTests()
        val multiResolvePsi = runReadAction {
            multiResolveIterable.map { it.element!! }
                .toList()
                .sortedBy { it.startOffset } // for stable result ordering
        }

        TestCase.assertEquals(2, multiResolvePsi.size)
        run {
            val result = multiResolvePsi[0] as KtLightMethod
            assertEquals("fun foo(a: String, b: Int) {}", result.originalElement.text)
        }
        run {
            val result = multiResolvePsi[1] as KtLightMethod
            assertEquals("fun foo(a: Int) {}", result.originalElement.text)
        }
    }
}