// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.fir.uast.test

import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.uast.test.common.kotlin.UastApiFixtureTestBase
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class FirUastApiFixtureTest : KotlinLightCodeInsightFixtureTestCase(), UastApiFixtureTestBase {

    override val pluginMode: KotlinPluginMode
        get() = KotlinPluginMode.K2

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
        checkCallableReferenceWithGeneric_convertedToSAM(myFixture, isK2 = true)
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

    fun testTypeOfUnresolvedErrorInThrowExpression() {
        checkTypeOfUnresolvedErrorInThrowExpression(myFixture)
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

    fun testGenericParameterSubtype() {
        checkGenericParameterSubtype(myFixture)
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

    fun testExpressionTypeForConstructorDelegationCall() {
        checkExpressionTypeForConstructorDelegationCall(myFixture)
    }

    fun testFlexibleFunctionalInterfaceType() {
        checkFlexibleFunctionalInterfaceType(myFixture)
    }

    fun testInvokedLambdaBody() {
        checkInvokedLambdaBody(myFixture)
    }

    fun testImplicitReceiver() {
        checkImplicitReceiver(myFixture)
    }

    fun testImplicitReceiver_extensionFunction() {
        checkImplicitReceiver_extensionFunction(myFixture)
    }

    fun testImplicitReceiver_insideInterface() {
        checkImplicitReceiver_insideInterface(myFixture)
    }

    fun testImplicitReceiver_interfaceHierarchy() {
        checkImplicitReceiver_interfaceHierarchy(myFixture)
    }

    fun testImplicitReceiver_interfaceHierarchy_smartcast() {
        checkImplicitReceiver_interfaceHierarchy_smartcast(myFixture)
    }

    fun testLambdaImplicitParameters() {
        checkLambdaImplicitParameters(myFixture)
    }

    fun testLambdaBodyAsParentOfDestructuringDeclaration() {
        checkLambdaBodyAsParentOfDestructuringDeclaration(myFixture)
    }

    fun testUnclosedLazyValueBody() {
        checkUnclosedLazyValueBody(myFixture)
    }

    fun testIdentifierOfNullableExtensionReceiver() {
        checkIdentifierOfNullableExtensionReceiver(myFixture)
    }

    fun testReceiverTypeOfExtensionFunction() {
        checkReceiverTypeOfExtensionFunction(myFixture)
    }

    fun testReceiverTypeOfExtensionFunction_superType() {
        checkReceiverTypeOfExtensionFunction_superType(myFixture)
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

    fun testEnumAsAnnotationAttributeValueEvaluation() {
        checkEnumAsAnnotationAttributeValueEvaluation(myFixture)
    }

    fun testJavaConstantEvaluation() {
        checkJavaConstantEvaluation(myFixture)
    }
}