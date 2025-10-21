// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.fir.uast.test

import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.uast.test.common.kotlin.LightClassBehaviorTestBase
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class FirLightClassBehaviorTest : KotlinLightCodeInsightFixtureTestCase(), LightClassBehaviorTestBase {

    override val pluginMode: KotlinPluginMode
        get() = KotlinPluginMode.K2

    override fun getProjectDescriptor(): LightProjectDescriptor =
        KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()

    fun testIdentifierOffsets() {
        checkIdentifierOffsets(myFixture)
    }

    fun testPropertyAccessorOffsets() {
        checkPropertyAccessorOffsets(myFixture)
    }

    fun testFunctionModifierListOffset() {
        checkFunctionModifierListOffsets(myFixture)
    }

    fun testPropertyAccessorModifierListOffsets() {
        checkPropertyAccessorModifierListOffsets(myFixture)
    }

    fun testLocalClassCaching() {
        checkLocalClassCaching(myFixture)
    }

    fun testAnnotationsOnClassCaching() {
        annotationsOnClassCaching(myFixture)
    }

    fun testThrowsList() {
        checkThrowsList(myFixture)
    }

    fun testFinalModifierOnEnumMembers() {
        checkFinalModifierOnEnumMembers(myFixture)
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

    fun testDefaultValueOfAnnotation_Kotlin() {
        checkDefaultValueOfAnnotation_Kotlin(myFixture)
    }

    fun testDefaultValueOfAnnotation_Java() {
        checkDefaultValueOfAnnotation_Java(myFixture)
    }

    fun testAnnotationParameterReference() {
        checkAnnotationParameterReference(myFixture)
    }

    fun testContainingFile() {
        checkContainingFile(myFixture)
    }

    fun testContainingFileInFacadeFiles() {
        checkContainingFileInFacadeFiles(myFixture)
    }
}