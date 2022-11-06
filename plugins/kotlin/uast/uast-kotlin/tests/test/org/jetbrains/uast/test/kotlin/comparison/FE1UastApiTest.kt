/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.uast.test.kotlin.comparison

import com.intellij.testFramework.TestDataPath
import org.jetbrains.kotlin.idea.test.JUnit3RunnerWithInners
import org.jetbrains.kotlin.idea.base.test.KotlinRoot
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.uast.UFile
import org.jetbrains.uast.test.common.kotlin.UastApiTestBase
import org.jetbrains.uast.test.kotlin.env.AbstractFE1UastTest
import org.junit.runner.RunWith
import java.io.File

@RunWith(JUnit3RunnerWithInners::class)
class FE1UastApiTest : AbstractFE1UastTest() {
    override fun check(testName: String, file: UFile) {
        // Bogus
    }

    @TestMetadata("uast-kotlin-fir/testData/declaration")
    @TestDataPath("/")
    @RunWith(JUnit3RunnerWithInners::class)
    class Declaration : AbstractFE1UastTest(), UastApiTestBase {
        override var testDataDir = KotlinRoot.DIR.resolve("uast/uast-kotlin-fir/testData/declaration")

        override val isFirUastPlugin: Boolean = false

        override fun check(testName: String, file: UFile) {
            // Bogus
        }

        @TestMetadata("retention.kt")
        fun testRetention() {
            doTest("retention", ::checkCallbackForRetention)
        }

        @TestMetadata("returns.kt")
        fun testReturnJumpTargets() {
            doTest("returns", ::checkReturnJumpTargets)
        }
    }

    @TestMetadata("uast-kotlin/testData")
    @TestDataPath("/")
    @RunWith(JUnit3RunnerWithInners::class)
    class Legacy : AbstractFE1UastTest(), UastApiTestBase {
        override var testDataDir: File = KotlinRoot.DIR.resolve("uast/uast-kotlin/tests/testData")

        override val isFirUastPlugin: Boolean = false

        override fun check(testName: String, file: UFile) {
            // Bogus
        }

        @TestMetadata("AnnotationParameters.kt")
        fun testAnnotationParameters() {
            doTest("AnnotationParameters", ::checkCallbackForAnnotationParameters)
        }

        @TestMetadata("StringTemplateInClass.kt")
        fun testStringTemplateInClass() {
            doTest("StringTemplateInClass", ::checkCallbackForStringTemplateInClass)
        }

        @TestMetadata("StringTemplateWithVar.kt")
        fun testStringTemplateWithVar() {
            doTest("StringTemplateWithVar", ::checkCallbackForStringTemplateWithVar)
        }

        @TestMetadata("NameContainingFile.kt")
        fun testNameContainingFile() {
            doTest("NameContainingFile", ::checkCallbackForNameContainingFile)
        }

        @TestMetadata("DefaultImpls.kt")
        fun testDefaultImpls() {
            doTest("DefaultImpls", ::checkCallbackForDefaultImpls)
        }

        @TestMetadata("ParameterPropertyWithAnnotation.kt")
        fun testParameterPropertyWithAnnotation() {
            doTest("ParameterPropertyWithAnnotation", ::checkCallbackForParameterPropertyWithAnnotation)
        }

        @TestMetadata("TypeInAnnotation.kt")
        fun testTypeInAnnotation() {
            doTest("TypeInAnnotation", ::checkCallbackForTypeInAnnotation)
        }

        @TestMetadata("ElvisType.kt")
        fun testElvisType() {
            doTest("ElvisType", ::checkCallbackForElvisType)
        }

        @TestMetadata("IfStatement.kt")
        fun testIfStatement() {
            doTest("IfStatement", ::checkCallbackForIfStatement)
        }

        @TestMetadata("WhenStringLiteral.kt")
        fun testWhenStringLiteral() {
            doTest("WhenStringLiteral", ::checkCallbackForWhenStringLiteral)
        }

        @TestMetadata("WhenAndDestructing.kt")
        fun testWhenAndDestructing() {
            doTest("WhenAndDestructing", ::checkCallbackForWhenAndDestructing)
        }

        @TestMetadata("BrokenMethod.kt")
        fun testBrokenMethod() {
            doTest("BrokenMethod", ::checkCallbackForBrokenMethod)
        }

        @TestMetadata("EnumValuesConstructors.kt")
        fun testEnumValuesConstructors() {
            doTest("EnumValuesConstructors", ::checkCallbackForEnumValuesConstructors)
        }

        @TestMetadata("EnumValueMembers.kt")
        fun testEnumValueMembers() {
            doTest("EnumValueMembers", ::checkCallbackForEnumValueMembers)
        }

        @TestMetadata("SimpleAnnotated.kt")
        fun testSimpleAnnotated() {
            doTest("SimpleAnnotated", ::checkCallbackForSimpleAnnotated)
        }

        @TestMetadata("SuperCalls.kt")
        fun testSuperCalls() {
            doTest("SuperCalls", ::checkCallbackForSuperCalls)
        }

        @TestMetadata("Anonymous.kt")
        fun testAnonymous() {
            doTest("Anonymous", ::checkCallbackForAnonymous)
        }

        @TestMetadata("TypeAliases.kt")
        fun testTypeAliases() {
            doTest("TypeAliases", ::checkCallbackForTypeAliases)
        }

        @TestMetadata("AnnotationComplex.kt")
        fun testAnnotationComplex() {
            doTest("AnnotationComplex", ::checkCallbackForAnnotationComplex)
        }

        @TestMetadata("ParametersDisorder.kt")
        fun testParametersDisorder() {
            doTest("ParametersDisorder", ::checkCallbackForParametersDisorder)
        }

        @TestMetadata("Lambdas.kt")
        fun testLambdas() {
            doTest("Lambdas", ::checkCallbackForLambdas)
        }

        @TestMetadata("LocalDeclarations.kt")
        fun testLocalDeclarations() {
            doTest("LocalDeclarations", ::checkCallbackForLocalDeclarations)
        }

        @TestMetadata("Elvis.kt")
        fun testElvis() {
            doTest("Elvis", ::checkCallbackForElvis)
        }

        @TestMetadata("TypeReferences.kt")
        fun testTypeReferences() {
            doTest("TypeReferences", ::checkCallbackForTypeReferences)
        }

        @TestMetadata("ReifiedReturnType.kt")
        fun testReifiedReturnType() {
            doTest("ReifiedReturnType", ::checkCallbackForReifiedReturnType)
        }

        @TestMetadata("ReifiedParameters.kt")
        fun testReifiedParameters() {
            doTest("ReifiedParameters", ::checkCallbackForReifiedParameters)
        }

        @TestMetadata("LambdaParameters.kt")
        fun testLambdaParameters() {
            doTest("LambdaParameters", ::checkCallbackForLambdaParameters)
        }

        @TestMetadata("ComplicatedTypes.kt")
        fun testComplicatedTypes() {
            doTest("ComplicatedTypes", ::checkCallbackForComplicatedTypes)
        }

        @TestMetadata("SAM.kt")
        fun testSAM() {
            doTest("SAM", ::checkCallbackForSAM)
        }

        @TestMetadata("Simple.kt")
        fun testSimple() {
            doTest("Simple", ::checkCallbackForSimple)
        }
        
        @TestMetadata("StringTemplateComplexForUInjectionHost.kt")
        fun testStringTemplateComplexForUInjectionHost() {
            doTest("StringTemplateComplexForUInjectionHost", ::checkCallbackForComplexStrings)
        }

        @TestMetadata("WhenIs.kt")
        fun testWhenIs() {
            doTest("WhenIs", ::checkSwitchYieldTargets)
        }
    }
}
