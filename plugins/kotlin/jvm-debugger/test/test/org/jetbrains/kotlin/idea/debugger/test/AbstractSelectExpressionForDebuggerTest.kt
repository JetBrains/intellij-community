// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.test

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.debugger.core.KotlinEditorTextProvider
import org.jetbrains.kotlin.idea.debugger.core.withCustomConfiguration
import org.jetbrains.kotlin.idea.test.*
import org.junit.Assert

sealed class AbstractSelectExpressionForDebuggerTest : KotlinLightCodeInsightFixtureTestCase() {
    override fun setUp() {
        super.setUp()
        invalidateLibraryCache(project)
    }

    protected abstract val expectedDirectiveName: String

    protected fun doTest(@Suppress("UNUSED_PARAMETER") unused: String, useAnalysisApi: Boolean) {
        val testFile = dataFile()

        myFixture.configureByFile(testFile)
        val fileText = myFixture.file.text

        val selectedExpression: PsiElement? = ApplicationManager.getApplication().executeOnPooledThread<PsiElement?> {
            runReadAction {
                val allowMethodCalls = !InTextDirectivesUtils.isDirectiveDefined(fileText, "DISALLOW_METHOD_CALLS")
                val elementAt = myFixture.file?.findElementAt(myFixture.caretOffset)!!

                KotlinEditorTextProvider.withCustomConfiguration(useAnalysisApi = useAnalysisApi) { provider ->
                    provider.findEvaluationTarget(elementAt, allowMethodCalls)
                }
            }
        }.get()

        val actual = selectedExpression?.text ?: "null"

        val expectedCustom = InTextDirectivesUtils.findStringWithPrefixes(fileText, "// $expectedDirectiveName: ")
        if (expectedCustom != null) {
            Assert.assertEquals(expectedCustom, actual)
        } else {
            val expectedFile = KotlinTestUtils.replaceExtension(testFile, "txt")
            if (expectedFile.exists()) {
                KotlinTestUtils.assertEqualsToFile(expectedFile, actual)
            } else {
                val expected = InTextDirectivesUtils.findStringWithPrefixes(fileText, "// EXPECTED: ")
                Assert.assertEquals("Another expression should be selected", expected, actual)
            }
        }
    }

    override fun getProjectDescriptor(): KotlinLightProjectDescriptor {
        return KotlinLightProjectDescriptor.INSTANCE
    }
}

abstract class AbstractSelectExpressionForDebuggerTestWithAnalysisApi : AbstractSelectExpressionForDebuggerTest() {
    override val expectedDirectiveName: String
        get() = "EXPECTED_ANALYSIS_API"

    protected open fun doTest(unused: String) = doTest(unused, useAnalysisApi = true)
}

abstract class AbstractSelectExpressionForDebuggerTestWithLegacyImplementation : AbstractSelectExpressionForDebuggerTest() {
    override val expectedDirectiveName: String
        get() = "EXPECTED_LEGACY"

    fun doTest(unused: String) = doTest(unused, useAnalysisApi = false)
}