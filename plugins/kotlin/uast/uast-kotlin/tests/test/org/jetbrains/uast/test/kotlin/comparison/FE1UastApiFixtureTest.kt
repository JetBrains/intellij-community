// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.test.kotlin.org.jetbrains.uast.test.kotlin.comparison

import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.uast.test.common.kotlin.UastApiFixtureTestBase
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class FE1UastApiFixtureTest : KotlinLightCodeInsightFixtureTestCase(), UastApiFixtureTestBase {

    override val pluginMode: KotlinPluginMode
        get() = KotlinPluginMode.K1

    override fun getProjectDescriptor(): LightProjectDescriptor =
        KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()

    fun testAssigningArrayElementType() {
        checkAssigningArrayElementType(myFixture)
    }

    fun testArgumentForParameter_smartcast() {
        checkArgumentForParameter_smartcast(myFixture)
    }

    fun testCallableReferenceWithGeneric() {
        checkCallableReferenceWithGeneric(myFixture)
    }

    fun testCallableReferenceWithGeneric_convertedToSAM() {
        checkCallableReferenceWithGeneric_convertedToSAM(myFixture, isK2 = false)
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

    fun testTypesOfDeprecatedHiddenProperty_noAccessor() {
        checkTypesOfDeprecatedHiddenProperty_noAccessor(myFixture)
    }

    fun testTypesOfDeprecatedHiddenProperty_getter() {
        checkTypesOfDeprecatedHiddenProperty_getter(myFixture)
    }

    fun testTypesOfDeprecatedHiddenProperty_setter() {
        checkTypesOfDeprecatedHiddenProperty_setter(myFixture)
    }

    fun testTypesOfDeprecatedHiddenProperty_accessors() {
        checkTypesOfDeprecatedHiddenProperty_accessors(myFixture)
    }

    fun testReifiedTypeNullability() {
        checkReifiedTypeNullability(myFixture)
    }

    fun testReifiedTypeNullability_generic() {
        checkReifiedTypeNullability_generic(myFixture)
    }

    fun testInheritedGenericTypeNullability() {
        checkInheritedGenericTypeNullability(myFixture)
    }

    fun testInheritedGenericTypeNullability_propertyAndAccessor() {
        checkInheritedGenericTypeNullability_propertyAndAccessor(myFixture)
    }

    fun testGenericTypeNullability_reified() {
        checkGenericTypeNullability_reified(myFixture)
    }

    fun testImplicitReceiverType() {
        checkImplicitReceiverType(myFixture)
    }

    fun testSubstitutedReceiverType() {
        checkSubstitutedReceiverType(myFixture)
    }

    fun testJavaStaticMethodReceiverType() {
        checkJavaStaticMethodReceiverType(myFixture)
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

    fun testSourcePsiOfLazyPropertyAccessor() {
        checkSourcePsiOfLazyPropertyAccessor(myFixture)
    }

    fun testTextRangeOfLocalVariable() {
        checkTextRangeOfLocalVariable(myFixture)
    }

    fun testNameReferenceVisitInConstructorCall() {
        checkNameReferenceVisitInConstructorCall(myFixture)
    }

    fun testNoArgConstructorSourcePsi() {
        checkNoArgConstructorSourcePsi(myFixture)
    }

    fun testNullLiteral() {
        checkNullLiteral(myFixture)
    }

    fun testStringConcatInAnnotationValue() {
        checkStringConcatInAnnotationValue(myFixture)
    }

    fun testLocalPropertyInitializerEvaluate_String() {
        checkLocalPropertyInitializerEvaluation_String(myFixture)
    }

    fun testLocalPropertyInitializerEvaluate_Numeric() {
        checkLocalPropertyInitializerEvaluation_Numeric(myFixture)
    }
}