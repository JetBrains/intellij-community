// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.test.kotlin.org.jetbrains.uast.test.kotlin.comparison

import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.uast.test.common.kotlin.LightClassBehaviorTestBase
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class FE1LightClassBehaviorTest : KotlinLightCodeInsightFixtureTestCase(), LightClassBehaviorTestBase {
    override val isFirUastPlugin: Boolean = false

    override fun getProjectDescriptor(): LightProjectDescriptor =
        KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()

    fun testIdentifierOffsets() {
        checkIdentifierOffsets(myFixture)
    }

    fun testPropertyAccessorOffsets() {
        checkPropertyAccessorOffsets(myFixture)
    }

    fun testFunctionModifierListOffsets() {
        checkFunctionModifierListOffsets(myFixture)
    }

    fun testPropertyAccessorModifierListOffsets() {
        checkPropertyAccessorModifierListOffsets(myFixture)
    }

    fun testThrowsList() {
        checkThrowsList(myFixture)
    }

    fun testAnnotationOnPsiType() {
        checkAnnotationOnPsiType(myFixture)
    }

    fun testAnnotationOnPsiTypeArgument() {
        checkAnnotationOnPsiTypeArgument(myFixture)
    }
}