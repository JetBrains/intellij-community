// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.introduce

import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.util.ui.UIUtil
import org.jetbrains.kotlin.idea.k2.refactoring.introduce.extractFunction.KotlinFirExtractFunctionHandler
import org.jetbrains.kotlin.idea.test.IDEA_TEST_DATA_DIR
import org.jetbrains.kotlin.idea.test.JUnit3RunnerWithInners
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.junit.runner.RunWith
import com.intellij.java.refactoring.ExtractMethodAndDuplicatesInplaceTest.Companion.nextTemplateVariable
import com.intellij.java.refactoring.ExtractMethodAndDuplicatesInplaceTest.Companion.renameTemplate
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import java.io.File


@RunWith(JUnit3RunnerWithInners::class)
class FirInplaceExtractFunctionTest : KotlinLightCodeInsightFixtureTestCase() {
    override fun isFirPlugin(): Boolean = true

    override val testDataDirectory = IDEA_TEST_DATA_DIR.resolve("refactoring/extractFunctionInplace")

    override fun getProjectDescriptor(): LightProjectDescriptor {
        return KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
    }

    fun testStringTemplate() {
        doTest()
    }

    fun testEmptySpaces() {
        doTest()
    }

    fun testSearchForCallAfter() {
        doTest()
    }

    fun testStringTemplateWithNameConflict() {
        doTest(changedName = "substring")
    }

    fun testConflictNameNotAccepted() {
        doTest(changedName = "conflict", checkResult = false)
        assertTrue(getActiveTemplate() != null)
    }

    fun testConsecutiveDuplicates() {
        doTest(changedName = "renamed")
    }

    fun testExposedAssignment() {
        doTest(changedName = "getX")
    }

    fun doTest(changedName: String? = null, checkResult: Boolean = true) {
        TemplateManagerImpl.setTemplateTesting(testRootDisposable)
        myFixture.configureByFile("${getTestName(false)}.kt")
        KotlinFirExtractFunctionHandler().invoke(myFixture.project, myFixture.editor, myFixture.file, null)
        NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
        val template = getActiveTemplate()
        require(template != null) { "Failed to start refactoring" }
        if (changedName != null) {
            renameTemplate(template, changedName)
        }
        nextTemplateVariable(template)
        UIUtil.dispatchAllInvocationEvents()
        if (checkResult) {
            var expectedFile = "${getTestName(false)}.fir.after.kt"
            if (!File(testDataDirectory, expectedFile).exists()) {
                expectedFile = "${getTestName(false)}.after.kt"
            }

            myFixture.checkResultByFile(expectedFile)
        }
    }

    private fun getActiveTemplate() = TemplateManagerImpl.getTemplateState(editor)
}