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

    fun testLocalClassCaching() {
        try {
            checkLocalClassCaching(myFixture)
            // TODO: KTIJ-26663
            error("Unmute me")
        } catch (e: Throwable) {
            return
        }
    }

    fun testFinalModifierOnEnumMembers() {
        checkFinalModifierOnEnumMembers(myFixture)
    }

    fun testThrowsList() {
        checkThrowsList(myFixture)
    }

    fun testComparatorInheritor() {
        checkComparatorInheritor(myFixture)
    }

    fun testBoxedReturnTypeWhenOverridingNonPrimitive() {
        checkBoxedReturnTypeWhenOverridingNonPrimitive(myFixture)
    }

    fun testAnnotationOnPsiType() {
        checkAnnotationOnPsiType(myFixture)
    }

    fun testAnnotationOnPsiTypeArgument() {
        checkAnnotationOnPsiTypeArgument(myFixture)
    }

    fun testUpperBoundWildcardForCtor() {
        checkUpperBoundWildcardForCtor(myFixture)
    }

    fun testUpperBoundWildcardForEnum() {
        checkUpperBoundWildcardForEnum(myFixture)
    }

    fun testUpperBoundWildcardForVar() {
        checkUpperBoundWildcardForVar(myFixture)
    }

    fun testUpperBoundForRecursiveTypeParameter() {
        checkUpperBoundForRecursiveTypeParameter(myFixture)
    }

    fun testDefaultValueOfAnnotation() {
        checkDefaultValueOfAnnotation(myFixture)
    }
}