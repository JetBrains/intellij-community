// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.test.kotlin

import org.jetbrains.uast.UFile
import org.jetbrains.uast.kotlin.KotlinConverter
import org.junit.Test

abstract class SimpleKotlinRenderLogTest : AbstractKotlinUastTest(), AbstractKotlinRenderLogTest {
    override fun check(testName: String, file: UFile) = super.check(testName, file)

    class LocalDeclarations : SimpleKotlinRenderLogTest() {
        @Test
        fun testLocalDeclarations() = doTest("LocalDeclarations")
    }

    class Simple : SimpleKotlinRenderLogTest() {
        @Test
        fun testSimple() = doTest("Simple")
    }

    class WhenIs : SimpleKotlinRenderLogTest() {
        @Test
        fun testWhenIs() = doTest("WhenIs")
    }

    class DefaultImpls : SimpleKotlinRenderLogTest() {
        @Test
        fun testDefaultImpls() = doTest("DefaultImpls")
    }

    class Bitwise : SimpleKotlinRenderLogTest() {
        @Test
        fun testBitwise() = doTest("Bitwise")
    }

    class Elvis : SimpleKotlinRenderLogTest() {
        @Test
        fun testElvis() = doTest("Elvis")
    }

    class PropertyAccessors : SimpleKotlinRenderLogTest() {
        @Test
        fun testPropertyAccessors() = doTest("PropertyAccessors")
    }

    class PropertyInitializer : SimpleKotlinRenderLogTest() {
        @Test
        fun testPropertyInitializer() = doTest("PropertyInitializer")
    }

    class PropertyInitializerWithoutSetter : SimpleKotlinRenderLogTest() {
        @Test
        fun testPropertyInitializerWithoutSetter() = doTest("PropertyInitializerWithoutSetter")
    }

    class AnnotationParameters : SimpleKotlinRenderLogTest() {
        @Test
        fun testAnnotationParameters() = doTest("AnnotationParameters")
    }

    class EnumValueMembers : SimpleKotlinRenderLogTest() {
        @Test
        fun testEnumValueMembers() = doTest("EnumValueMembers")
    }

    class EnumValuesConstructors : SimpleKotlinRenderLogTest() {
        @Test
        fun testEnumValuesConstructors() = doTest("EnumValuesConstructors")
    }

    class StringTemplate : SimpleKotlinRenderLogTest() {
        @Test
        fun testStringTemplate() = doTest("StringTemplate")
    }

    class StringTemplateComplex : SimpleKotlinRenderLogTest() {
        @Test
        fun testStringTemplateComplex() = doTest("StringTemplateComplex")
    }

    class StringTemplateComplexForUInjectionHost : SimpleKotlinRenderLogTest() {
        @Test
        fun testStringTemplateComplexForUInjectionHost() = withForceUInjectionHostValue {
            doTest("StringTemplateComplexForUInjectionHost")
        }
    }

    class QualifiedConstructorCall : SimpleKotlinRenderLogTest() {
        @Test
        fun testQualifiedConstructorCall() = doTest("QualifiedConstructorCall")
    }

    class PropertyDelegate : SimpleKotlinRenderLogTest() {
        @Test
        fun testPropertyDelegate() = doTest("PropertyDelegate")
    }

    class LocalVariableWithAnnotation : SimpleKotlinRenderLogTest() {
        @Test
        fun testLocalVariableWithAnnotation() = doTest("LocalVariableWithAnnotation")
    }

    class PropertyWithAnnotation : SimpleKotlinRenderLogTest() {
        @Test
        fun testPropertyWithAnnotation() = doTest("PropertyWithAnnotation")
    }

    class IfStatement : SimpleKotlinRenderLogTest() {
        @Test
        fun testIfStatement() = doTest("IfStatement")
    }

    class InnerClasses : SimpleKotlinRenderLogTest() {
        @Test
        fun testInnerClasses() = doTest("InnerClasses")
    }

    class SimpleScript : SimpleKotlinRenderLogTest() {
        @Test
        fun testSimpleScript() = doTest("SimpleScript") { testName, file -> check(testName, file, false) }
    }

    class DestructuringDeclaration : SimpleKotlinRenderLogTest() {
        @Test
        fun testDestructuringDeclaration() = doTest("DestructuringDeclaration")
    }

    class DefaultParameterValues : SimpleKotlinRenderLogTest() {
        @Test
        fun testDefaultParameterValues() = doTest("DefaultParameterValues")
    }

    class ParameterPropertyWithAnnotation : SimpleKotlinRenderLogTest() {
        @Test
        fun testParameterPropertyWithAnnotation() = doTest("ParameterPropertyWithAnnotation")
    }

    class ParametersWithDefaultValues : SimpleKotlinRenderLogTest() {
        @Test
        fun testParametersWithDefaultValues() = doTest("ParametersWithDefaultValues")
    }

    class UnexpectedContainer : SimpleKotlinRenderLogTest() {
        @Test
        fun testUnexpectedContainer() = doTest("UnexpectedContainerException")
    }

    class WhenStringLiteral : SimpleKotlinRenderLogTest() {
        @Test
        fun testWhenStringLiteral() = doTest("WhenStringLiteral")
    }

    class WhenAndDestructing : SimpleKotlinRenderLogTest() {
        @Test
        fun testWhenAndDestructing() = doTest("WhenAndDestructing") { testName, file -> check(testName, file, false) }
    }

    class SuperCalls : SimpleKotlinRenderLogTest() {
        @Test
        fun testSuperCalls() = doTest("SuperCalls")
    }

    class Constructors : SimpleKotlinRenderLogTest() {
        @Test
        fun testConstructors() = doTest("Constructors")
    }

    class ClassAnnotation : SimpleKotlinRenderLogTest() {
        @Test
        fun testClassAnnotation() = doTest("ClassAnnotation")
    }

    class ReceiverFun : SimpleKotlinRenderLogTest() {
        @Test
        fun testReceiverFun() = doTest("ReceiverFun")
    }

    class Anonymous : SimpleKotlinRenderLogTest() {
        @Test
        fun testAnonymous() = doTest("Anonymous")
    }

    class AnnotationComplex : SimpleKotlinRenderLogTest() {
        @Test
        fun testAnnotationComplex() = doTest("AnnotationComplex")
    }

    class ParametersDisorder : SimpleKotlinRenderLogTest() {
        @Test
        fun testParametersDisorder() = doTest("ParametersDisorder") { testName, file ->
            // disabled due to inconsistent parents for 2-receivers call (KT-22344)
            check(testName, file, false)
        }
    }

    class Lambdas : SimpleKotlinRenderLogTest() {
        @Test
        fun testLambdas() = doTest("Lambdas")
    }

    class TypeReferences : SimpleKotlinRenderLogTest() {
        @Test
        fun testTypeReferences() = doTest("TypeReferences")
    }

    class Delegate : SimpleKotlinRenderLogTest() {
        @Test
        fun testDelegate() = doTest("Delegate")
    }

    class ConstructorDelegate : SimpleKotlinRenderLogTest() {
        @Test
        fun testConstructorDelegate() = doTest("ConstructorDelegate")
    }

    class LambdaReturn : SimpleKotlinRenderLogTest() {
        @Test
        fun testLambdaReturn() = doTest("LambdaReturn")
    }

    class Reified : SimpleKotlinRenderLogTest() {
        @Test
        fun testReified() = doTest("Reified")
    }

    class ReifiedReturnType : SimpleKotlinRenderLogTest() {
        @Test
        fun testReifiedReturnType() = doTest("ReifiedReturnType")
    }

    class ReifiedParameters : SimpleKotlinRenderLogTest() {
        @Test
        fun testReifiedParameters() = doTest("ReifiedParameters")
    }

    class Suspend : SimpleKotlinRenderLogTest() {
        @Test
        fun testSuspend() = doTest("Suspend")
    }

    class DeprecatedHidden : SimpleKotlinRenderLogTest() {
        @Test
        fun testDeprecatedHidden() = doTest("DeprecatedHidden")
    }

    class TryCatch : SimpleKotlinRenderLogTest() {
        @Test
        fun testTryCatch() = doTest("TryCatch")
    }

    class AnnotatedExpressions : SimpleKotlinRenderLogTest() {
        @Test
        fun testAnnotatedExpressions() = doTest("AnnotatedExpressions")
    }

    class NonTrivialIdentifiers : SimpleKotlinRenderLogTest() {
        @Test
        fun testNonTrivialIdentifiers() = doTest("NonTrivialIdentifiers")
    }

    class TypeAliasExpansionWithOtherAliasInArgument : SimpleKotlinRenderLogTest() {
        @Test
        fun testTypeAliasExpansionWithOtherAliasInArgument() = doTest("TypeAliasExpansionWithOtherAliasInArgument")
    }

    class Comments : SimpleKotlinRenderLogTest() {
        @Test
        fun testComments() = doTest("Comments")
    }

    class BrokenDataClass : SimpleKotlinRenderLogTest() {
        @Test
        fun testBrokenDataClass() = doTest("BrokenDataClass")
    }
}

fun withForceUInjectionHostValue(call: () -> Unit) {
    val prev = KotlinConverter.forceUInjectionHost
    KotlinConverter.forceUInjectionHost = true
    try {
        call.invoke()
    } finally {
        KotlinConverter.forceUInjectionHost = prev
    }
}