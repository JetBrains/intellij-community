// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.test.kotlin

import org.jetbrains.uast.UFile
import org.junit.Ignore
import org.junit.Test
import java.io.File

abstract class KotlinIDERenderLogTest : AbstractKotlinUastLightCodeInsightFixtureTest(), AbstractKotlinRenderLogTest {

    override fun getTestFile(testName: String, ext: String): File {
        if (ext.endsWith(".txt")) {
            val testFile = super.getTestFile(testName, ext.removeSuffix(".txt") + "-ide.txt")
            if (testFile.exists()) return testFile
        }
        return super.getTestFile(testName, ext)
    }

    override fun check(testName: String, file: UFile) = super.check(testName, file)

    class LocalDeclarations : KotlinIDERenderLogTest() {
        @Test
        fun testLocalDeclarations() = doTest("LocalDeclarations")
    }

    class Simple : KotlinIDERenderLogTest() {
        @Test
        fun testSimple() = doTest("Simple")
    }

    class WhenIs : KotlinIDERenderLogTest() {
        @Test
        fun testWhenIs() = doTest("WhenIs")
    }

    class DefaultImpls : KotlinIDERenderLogTest() {
        @Test
        fun testDefaultImpls() = doTest("DefaultImpls")
    }

    class Bitwise : KotlinIDERenderLogTest() {
        @Test
        fun testBitwise() = doTest("Bitwise")
    }

    class Elvis : KotlinIDERenderLogTest() {
        @Test
        fun testElvis() = doTest("Elvis")
    }

    class PropertyAccessors : KotlinIDERenderLogTest() {
        @Test
        fun testPropertyAccessors() = doTest("PropertyAccessors")
    }

    class PropertyInitializer : KotlinIDERenderLogTest() {
        @Test
        fun testPropertyInitializer() = doTest("PropertyInitializer")
    }

    class PropertyInitializerWithoutSetter : KotlinIDERenderLogTest() {
        @Test
        fun testPropertyInitializerWithoutSetter() = doTest("PropertyInitializerWithoutSetter")
    }

    class AnnotationParameters : KotlinIDERenderLogTest() {
        @Test
        fun testAnnotationParameters() = doTest("AnnotationParameters")
    }

    class EnumValueMembers : KotlinIDERenderLogTest() {
        @Test
        fun testEnumValueMembers() = doTest("EnumValueMembers")
    }

    class EnumValuesConstructors : KotlinIDERenderLogTest() {
        @Test
        fun testEnumValuesConstructors() = doTest("EnumValuesConstructors")
    }

    class StringTemplate : KotlinIDERenderLogTest() {
        @Test
        fun testStringTemplate() = doTest("StringTemplate")
    }

    class StringTemplateComplex : KotlinIDERenderLogTest() {
        @Test
        fun testStringTemplateComplex() = doTest("StringTemplateComplex")
    }

    class StringTemplateComplexForUInjectionHost : KotlinIDERenderLogTest() {
        @Test
        fun testStringTemplateComplexForUInjectionHost() = withForceUInjectionHostValue {
            doTest("StringTemplateComplexForUInjectionHost")
        }
    }

    class QualifiedConstructorCall : KotlinIDERenderLogTest() {
        @Test
        fun testQualifiedConstructorCall() = doTest("QualifiedConstructorCall")
    }

    class PropertyDelegate : KotlinIDERenderLogTest() {
        @Test
        fun testPropertyDelegate() = doTest("PropertyDelegate")
    }

    class LocalVariableWithAnnotation : KotlinIDERenderLogTest() {
        @Test
        fun testLocalVariableWithAnnotation() = doTest("LocalVariableWithAnnotation")
    }

    class PropertyWithAnnotation : KotlinIDERenderLogTest() {
        @Test
        fun testPropertyWithAnnotation() = doTest("PropertyWithAnnotation")
    }

    class IfStatement : KotlinIDERenderLogTest() {
        @Test
        fun testIfStatement() = doTest("IfStatement")
    }

    class InnerClasses : KotlinIDERenderLogTest() {
        @Test
        fun testInnerClasses() = doTest("InnerClasses")
    }

    //class SimpleScript : KotlinIDERenderLogTest() {
    //    @Test
    //    fun testSimpleScript() = doTest("SimpleScript") { testName, file -> check(testName, file, false) }
    //}

    class DestructuringDeclaration : KotlinIDERenderLogTest() {
        @Test
        fun testDestructuringDeclaration() = doTest("DestructuringDeclaration")
    }

    class DefaultParameterValues : KotlinIDERenderLogTest() {
        @Test
        fun testDefaultParameterValues() = doTest("DefaultParameterValues")
    }

    class ParameterPropertyWithAnnotation : KotlinIDERenderLogTest() {
        @Test
        fun testParameterPropertyWithAnnotation() = doTest("ParameterPropertyWithAnnotation")
    }

    class ParametersWithDefaultValues : KotlinIDERenderLogTest() {
        @Test
        fun testParametersWithDefaultValues() = doTest("ParametersWithDefaultValues")
    }

    class UnexpectedContainer : KotlinIDERenderLogTest() {
        @Test
        fun testUnexpectedContainer() = doTest("UnexpectedContainerException")
    }

    class WhenStringLiteral : KotlinIDERenderLogTest() {
        @Test
        fun testWhenStringLiteral() = doTest("WhenStringLiteral")
    }

    class WhenAndDestructing : KotlinIDERenderLogTest() {
        @Test
        fun testWhenAndDestructing() = doTest("WhenAndDestructing") { testName, file -> check(testName, file, false) }
    }

    class SuperCalls : KotlinIDERenderLogTest() {
        @Test
        fun testSuperCalls() = doTest("SuperCalls")
    }

    class Constructors : KotlinIDERenderLogTest() {
        @Test
        fun testConstructors() = doTest("Constructors")
    }

    class ClassAnnotation : KotlinIDERenderLogTest() {
        @Test
        fun testClassAnnotation() = doTest("ClassAnnotation")
    }

    class ReceiverFun : KotlinIDERenderLogTest() {
        @Test
        fun testReceiverFun() = doTest("ReceiverFun")
    }

    class Anonymous : KotlinIDERenderLogTest() {
        @Test
        fun testAnonymous() = doTest("Anonymous")
    }

    class AnnotationComplex : KotlinIDERenderLogTest() {
        @Test
        fun testAnnotationComplex() = doTest("AnnotationComplex")
    }

    class ParametersDisorder : KotlinIDERenderLogTest() {
        @Test
        fun testParametersDisorder() = doTest("ParametersDisorder") { testName, file ->
            // disabled due to inconsistent parents for 2-receivers call (KT-22344)
            check(testName, file, false)
        }
    }

    class Lambdas : KotlinIDERenderLogTest() {
        @Test
        fun testLambdas() = doTest("Lambdas")
    }

    class TypeReferences : KotlinIDERenderLogTest() {
        @Test
        fun testTypeReferences() = doTest("TypeReferences")
    }

    class Delegate : KotlinIDERenderLogTest() {
        @Test
        fun testDelegate() = doTest("Delegate")
    }

    class ConstructorDelegate : KotlinIDERenderLogTest() {
        @Test
        fun testConstructorDelegate() = doTest("ConstructorDelegate") { testName, file ->
            // Igor Yakovlev told that delegation is a little bit broken in ULC and not expected to be fixed
            check(testName, file, false)
        }
    }

    class LambdaReturn : KotlinIDERenderLogTest() {
        @Test
        fun testLambdaReturn() = doTest("LambdaReturn")
    }

    class Reified : KotlinIDERenderLogTest() {
        @Test
        fun testReified() = doTest("Reified")
    }

    class ReifiedReturnType : KotlinIDERenderLogTest() {
        @Test
        fun testReifiedReturnType() = doTest("ReifiedReturnType")
    }

    class ReifiedParameters : KotlinIDERenderLogTest() {
        @Test
        fun testReifiedParameters() = doTest("ReifiedParameters")
    }

    class Suspend : KotlinIDERenderLogTest() {
        @Test
        fun testSuspend() = doTest("Suspend")
    }

    class DeprecatedHidden : KotlinIDERenderLogTest() {
        @Test
        fun testDeprecatedHidden() = doTest("DeprecatedHidden")
    }

    class TryCatch : KotlinIDERenderLogTest() {
        @Test
        fun testTryCatch() = doTest("TryCatch")
    }

    class Comments : KotlinIDERenderLogTest() {
        @Test
        fun testComments() = doTest("Comments")
    }
}
