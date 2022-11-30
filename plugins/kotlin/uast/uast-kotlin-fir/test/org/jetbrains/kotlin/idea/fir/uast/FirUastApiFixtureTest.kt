// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.uast

import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.uast.test.common.kotlin.UastApiFixtureTestBase
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class FirUastApiFixtureTest : KotlinLightCodeInsightFixtureTestCase(), UastApiFixtureTestBase {
    override val isFirUastPlugin: Boolean = true

    override fun isFirPlugin(): Boolean = true

    override fun getProjectDescriptor(): LightProjectDescriptor =
        KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()

    private fun doCheck(key: String, checkCallback: (JavaCodeInsightTestFixture) -> Unit) {
        checkCallback(myFixture)
    }

    fun testAssigningArrayElementType() {
        doCheck("AssigningArrayElementType", ::checkAssigningArrayElementType)
    }

    fun testDivByZero() {
        doCheck("DivByZero", ::checkDivByZero)
    }

    fun testDetailsOfDeprecatedHidden() {
        doCheck("DetailsOfDeprecatedHidden", ::checkDetailsOfDeprecatedHidden)
    }

    fun testImplicitReceiverType() {
        doCheck("ImplicitReceiverType", ::checkImplicitReceiverType)
    }

    fun testSubstitutedReceiverType() {
        doCheck("SubstitutedReceiverType", ::checkSubstitutedReceiverType)
    }

    fun testCallKindOfSamConstructor() {
        doCheck("CallKindOfSamConstructor", ::checkCallKindOfSamConstructor)
    }

    fun testExpressionTypeFromIncorrectObject() {
        doCheck("ExpressionTypeFromIncorrectObject", ::checkExpressionTypeFromIncorrectObject)
    }

}