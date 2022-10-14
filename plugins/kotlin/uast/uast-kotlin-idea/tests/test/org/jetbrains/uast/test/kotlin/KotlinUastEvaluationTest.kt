// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.test.kotlin

import com.intellij.testFramework.LightProjectDescriptor
import junit.framework.TestCase
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.uast.UField
import org.jetbrains.uast.evaluation.getEvaluationContextWithCaching
import org.jetbrains.uast.test.env.findUElementByTextFromPsi

class KotlinUastEvaluationTest : KotlinLightCodeInsightFixtureTestCase() {
    override fun getProjectDescriptor(): LightProjectDescriptor =
        KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()

    fun `test string concatenation expression`() {
        evaluationTest("\"foo\" + \"bar\"", expectedValue = "foobar", expectedValueForSimpleEvaluator = "foobar")
    }

    fun `test in closed range`() {
        evaluationTest("5 in 1..10", expectedValue = true, expectedValueForSimpleEvaluator = null)
    }

    private fun evaluationTest(expressionText: String, expectedValue: Any?, expectedValueForSimpleEvaluator: Any?) {
        val variable = myFixture.configureByText("test.kt", "val a = $expressionText")
            .findUElementByTextFromPsi<UField>("val a", strict = false)

        val evaluator = variable.getEvaluationContextWithCaching().getEvaluator(variable)

        val initializer = variable.uastInitializer!!
        val value = evaluator.evaluate(initializer)

        TestCase.assertEquals(expectedValue, value.toConstant()?.value)
        TestCase.assertEquals(expectedValueForSimpleEvaluator, initializer.evaluate())
    }
}