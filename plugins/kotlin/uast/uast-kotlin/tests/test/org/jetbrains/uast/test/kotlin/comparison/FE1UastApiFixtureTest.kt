// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.test.kotlin.org.jetbrains.uast.test.kotlin.comparison

import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.uast.test.common.kotlin.UastApiFixtureTestBase
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class FE1UastApiFixtureTest : KotlinLightCodeInsightFixtureTestCase(), UastApiFixtureTestBase {
    override val isFirUastPlugin: Boolean = false

    override fun getProjectDescriptor(): LightProjectDescriptor =
        KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()

    fun testAssigningArrayElementType() {
        checkAssigningArrayElementType(myFixture)
    }

    fun testArgumentForParameter_smartcast() {
        checkArgumentForParameter_smartcast(myFixture)
    }

    fun testDivByZero() {
        checkDivByZero(myFixture)
    }

    fun testDetailsOfDeprecatedHidden() {
        checkDetailsOfDeprecatedHidden(myFixture)
    }

    fun testTypesOfDeprecatedHidden() {
        checkTypesOfDeprecatedHidden(myFixture)
    }

    fun testTypesOfDeprecatedHiddenSuspend() {
        checkTypesOfDeprecatedHiddenSuspend(myFixture)
    }

    fun testReifiedTypeNullability() {
        checkReifiedTypeNullability(myFixture)
    }

    fun testInheritedGenericTypeNullability() {
        checkInheritedGenericTypeNullability(myFixture)
    }

    fun testInheritedGenericTypeNullability_propertyAndAccessor() {
        checkInheritedGenericTypeNullability_propertyAndAccessor(myFixture)
    }

    fun testImplicitReceiverType() {
        checkImplicitReceiverType(myFixture)
    }

    fun testSubstitutedReceiverType() {
        checkSubstitutedReceiverType(myFixture)
    }

    fun testUnderscoreOperatorForTypeArguments() {
        checkUnderscoreOperatorForTypeArguments(myFixture)
    }

    fun testCallKindOfSamConstructor() {
        checkCallKindOfSamConstructor(myFixture)
    }

    fun testExpressionTypeOfForEach() {
        checkExpressionTypeOfForEach(myFixture)
    }

    fun testExpressionTypeFromIncorrectObject() {
        checkExpressionTypeFromIncorrectObject(myFixture)
    }

    fun testExpressionTypeForCallToInternalOperator() {
        checkExpressionTypeForCallToInternalOperator(myFixture)
    }

    fun testFlexibleFunctionalInterfaceType() {
        checkFlexibleFunctionalInterfaceType(myFixture)
    }

    fun testInvokedLambdaBody() {
        checkInvokedLambdaBody(myFixture)
    }

    fun testLambdaImplicitParameters() {
        checkLambdaImplicitParameters(myFixture)
    }

    fun testLambdaBodyAsParentOfDestructuringDeclaration() {
        checkLambdaBodyAsParentOfDestructuringDeclaration(myFixture)
    }

    fun testIdentifierOfNullableExtensionReceiver() {
        checkIdentifierOfNullableExtensionReceiver(myFixture)
    }

    fun testReceiverTypeOfExtensionFunction() {
        checkReceiverTypeOfExtensionFunction(myFixture)
    }
}