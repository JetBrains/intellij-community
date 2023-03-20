// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.test.kotlin.analysis

import com.intellij.psi.util.PartiallyKnownString
import com.intellij.psi.util.StringEntry
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.uast.*
import org.jetbrains.uast.analysis.UNeDfaConfiguration
import org.jetbrains.uast.analysis.UStringEvaluator
import kotlin.test.fail as kotlinFail

abstract class AbstractKotlinUStringEvaluatorTest : KotlinLightCodeInsightFixtureTestCase() {
    override fun getProjectDescriptor(): LightProjectDescriptor =
        KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()

    private val PartiallyKnownString.debugConcatenation: String
        get() = buildString {
            for (segment in segments) {
                when (segment) {
                    is StringEntry.Known -> append("'").append(segment.value).append("'")
                    is StringEntry.Unknown -> {
                        segment.possibleValues
                            ?.map { it.debugConcatenation }
                            ?.sorted()
                            ?.joinTo(this, "|", "{", "}") { it }
                            ?: append("NULL")
                    }
                }
            }
        }

    protected fun doTest(
        @Language("kotlin") source: String,
        expected: String,
        additionalSetup: () -> Unit = {},
        configuration: () -> UNeDfaConfiguration<PartiallyKnownString> = { UNeDfaConfiguration() },
        additionalAssertions: (PartiallyKnownString) -> Unit = {}
    ) {
        additionalSetup()
        val file = myFixture.configureByText("myFile.kt", source)
        val elementAtCaret = file.findElementAt(myFixture.caretOffset).getUastParentOfType<UReturnExpression>()?.returnExpression
            ?: kotlinFail("Cannot find UElement at caret")
        val pks = UStringEvaluator().calculateValue(elementAtCaret, configuration()) ?: kotlinFail("Cannot evaluate string")
        LightJavaCodeInsightFixtureTestCase.assertEquals(expected, pks.debugConcatenation)
        additionalAssertions(pks)
    }
}