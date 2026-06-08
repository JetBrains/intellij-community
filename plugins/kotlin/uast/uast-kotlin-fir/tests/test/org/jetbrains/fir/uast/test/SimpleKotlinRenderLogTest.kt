// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.fir.uast.test

import org.jetbrains.fir.uast.test.env.kotlin.AbstractFirUastTest
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UFile
import org.jetbrains.uast.test.kotlin.AbstractKotlinRenderLogTest
import org.jetbrains.uast.test.kotlin.TEST_KOTLIN_MODEL_PATH
import org.junit.Test
import java.nio.file.Path

class SimpleKotlinRenderLogTest : AbstractFirUastTest(), AbstractKotlinRenderLogTest {
    override fun checkLeak(node: UElement) {
    }

    override val testBasePath: Path = TEST_KOTLIN_MODEL_PATH

    override fun check(fileName: String, file: UFile) {
        super<AbstractKotlinRenderLogTest>.check(getTestName(/* lowercaseFirstLetter = */ false), file)
    }

    @Test
    fun testLocalDeclarations() = doCheck("LocalDeclarations.kt")

    @Test
    fun testSimple() = doCheck("Simple.kt")

    @Test
    fun testWhenIs() = doCheck("WhenIs.kt")

    @Test
    fun testDefaultImpls() = doCheck("DefaultImpls.kt")

    @Test
    fun testBitwise() = doCheck("Bitwise.kt")

    @Test
    fun testElvis() = doCheck("Elvis.kt")

    @Test
    fun testPropertyAccessors() = doCheck("PropertyAccessors.kt")

    @Test
    fun testPropertyInitializer() = doCheck("PropertyInitializer.kt")

    @Test
    fun testPropertyInitializerWithoutSetter() = doCheck("PropertyInitializerWithoutSetter.kt")

    @Test
    fun testAnnotationParameters() = doCheck("AnnotationParameters.kt")

    @Test
    fun testEnumValueMembers() = doCheck("EnumValueMembers.kt")

    @Test
    fun testEnumValuesConstructors() = doCheck("EnumValuesConstructors.kt")

    @Test
    fun testStringTemplate() = doCheck("StringTemplate.kt")

    @Test
    fun testStringTemplateComplex() = doCheck("StringTemplateComplex.kt")

    @Test
    fun testStringTemplateComplexForUInjectionHost() = doCheck("StringTemplateComplexForUInjectionHost.kt")

    @Test
    fun testQualifiedConstructorCall() = doCheck("QualifiedConstructorCall.kt")

    @Test
    fun testLocalVariableWithAnnotation() = doCheck("LocalVariableWithAnnotation.kt")

    @Test
    fun testPropertyWithAnnotation() = doCheck("PropertyWithAnnotation.kt")

    @Test
    fun testIfStatement() = doCheck("IfStatement.kt")

    @Test
    fun testInnerClasses() = doCheck("InnerClasses.kt")

    // KTIJ-38566
    fun _testSimpleScript() = doCheck("SimpleScript.kt") { testName, file -> check(testName, file, false) }

    @Test
    fun testDestructuringDeclaration() = doCheck("DestructuringDeclaration.kt")

    @Test
    fun testDefaultParameterValues() = doCheck("DefaultParameterValues.kt")

    @Test
    fun testParameterPropertyWithAnnotation() = doCheck("ParameterPropertyWithAnnotation.kt")

    @Test
    fun testParametersWithDefaultValues() = doCheck("ParametersWithDefaultValues.kt")

    @Test
    fun testUnexpectedContainerException() = doCheck("UnexpectedContainerException.kt")

    @Test
    fun testWhenStringLiteral() = doCheck("WhenStringLiteral.kt")

    // KTIJ-38566
    fun _testWhenAndDestructing() = doCheck("WhenAndDestructing.kt") { testName, file -> check(testName, file, false) }

    @Test
    fun testSuperCalls() = doCheck("SuperCalls.kt")

    @Test
    fun testConstructors() = doCheck("Constructors.kt")

    @Test
    fun testReceiverFun() = doCheck("ReceiverFun.kt")

    @Test
    fun testAnonymous() = doCheck("Anonymous.kt")

    @Test
    fun testAnnotationComplex() = doCheck("AnnotationComplex.kt")

    // KTIJ-38566
    fun _testParametersDisorder() = doCheck("ParametersDisorder.kt") { testName, file ->
        // disabled due to inconsistent parents for 2-receivers call (KT-22344)
        check(testName, file, false)
    }

    @Test
    fun testLambdas() = doCheck("Lambdas.kt")

    @Test
    fun testTypeReferences() = doCheck("TypeReferences.kt")

    @Test
    fun testDelegate() = doCheck("Delegate.kt")

    @Test
    fun testConstructorDelegate() = doCheck("ConstructorDelegate.kt")

    @Test
    fun testLambdaReturn() = doCheck("LambdaReturn.kt")

    @Test
    fun testReified() = doCheck("Reified.kt")

    @Test
    fun testReifiedReturnType() = doCheck("ReifiedReturnType.kt")

    // KTIJ-38566
    fun _testReifiedParameters() = doCheck("ReifiedParameters.kt")

    @Test
    fun testSuspend() = doCheck("Suspend.kt")

    @Test
    fun testDeprecatedHidden() = doCheck("DeprecatedHidden.kt")

    @Test
    fun testTryCatch() = doCheck("TryCatch.kt")

    @Test
    fun testAnnotatedExpressions() = doCheck("AnnotatedExpressions.kt")

    @Test
    fun testNonTrivialIdentifiers() = doCheck("NonTrivialIdentifiers.kt")

    @Test
    fun testTypeAliasExpansionWithOtherAliasInArgument() = doCheck("TypeAliasExpansionWithOtherAliasInArgument.kt")

    @Test
    fun testComments() = doCheck("Comments.kt")

    @Test
    fun testBrokenDataClass() = doCheck("BrokenDataClass.kt")
}