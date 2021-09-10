/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.idea.fir.uast

import com.intellij.testFramework.TestDataPath
import org.jetbrains.kotlin.idea.fir.uast.env.kotlin.AbstractFirUastTest
import org.jetbrains.kotlin.idea.test.JUnit3RunnerWithInners
import org.jetbrains.kotlin.test.KotlinRoot
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.uast.UFile
import org.jetbrains.uast.test.common.kotlin.UastApiTestBase
import org.junit.runner.RunWith

@RunWith(JUnit3RunnerWithInners::class)
open class FirUastApiTest : AbstractFirUastTest() {
    override val isFirUastPlugin: Boolean = true

    override val basePath = KotlinRoot.DIR_PATH.resolve("uast")

    override fun check(filePath: String, file: UFile) {
        // Bogus
    }

    @TestMetadata("../uast-kotlin/testData")
    @TestDataPath("\$PROJECT_ROOT")
    @RunWith(JUnit3RunnerWithInners::class)
    class Legacy : FirUastApiTest(), UastApiTestBase {
        override val isFirUastPlugin: Boolean = true

        override fun check(filePath: String, file: UFile) {
            // Bogus
        }

        @TestMetadata("AnnotationParameters.kt")
        fun testAnnotationParameters() {
            doCheck("uast-kotlin/testData/AnnotationParameters.kt", ::checkCallbackForAnnotationParameters)
        }

        @TestMetadata("StringTemplateInClass.kt")
        fun testStringTemplateInClass() {
            doCheck("uast-kotlin/testData/StringTemplateInClass.kt", ::checkCallbackForStringTemplateInClass)
        }

        @TestMetadata("StringTemplateWithVar.kt")
        fun testStringTemplateWithVar() {
            doCheck("uast-kotlin/testData/StringTemplateWithVar.kt", ::checkCallbackForStringTemplateWithVar)
        }

        @TestMetadata("NameContainingFile.kt")
        fun testNameContainingFile() {
            doCheck("uast-kotlin/testData/NameContainingFile.kt", ::checkCallbackForNameContainingFile)
        }

        @TestMetadata("DefaultImpls.kt")
        fun testDefaultImpls() {
            doCheck("uast-kotlin/testData/DefaultImpls.kt", ::checkCallbackForDefaultImpls)
        }

        @TestMetadata("ParameterPropertyWithAnnotation.kt")
        fun testParameterPropertyWithAnnotation() {
            doCheck("uast-kotlin/testData/ParameterPropertyWithAnnotation.kt", ::checkCallbackForParameterPropertyWithAnnotation)
        }

        @TestMetadata("TypeInAnnotation.kt")
        fun testTypeInAnnotation() {
            doCheck("uast-kotlin/testData/TypeInAnnotation.kt", ::checkCallbackForTypeInAnnotation)
        }

        @TestMetadata("ElvisType.kt")
        fun testElvisType() {
            doCheck("uast-kotlin/testData/ElvisType.kt", ::checkCallbackForElvisType)
        }

        @TestMetadata("IfStatement.kt")
        fun testIfStatement() {
            doCheck("uast-kotlin/testData/IfStatement.kt", ::checkCallbackForIfStatement)
        }

        @TestMetadata("WhenStringLiteral.kt")
        fun testWhenStringLiteral() {
            doCheck("uast-kotlin/testData/WhenStringLiteral.kt", ::checkCallbackForWhenStringLiteral)
        }

        @TestMetadata("WhenAndDestructing.kt")
        fun testWhenAndDestructing() {
            doCheck("uast-kotlin/testData/WhenAndDestructing.kt", ::checkCallbackForWhenAndDestructing)
        }

        @TestMetadata("BrokenMethod.kt")
        fun testBrokenMethod() {
            doCheck("uast-kotlin/testData/BrokenMethod.kt", ::checkCallbackForBrokenMethod)
        }

        @TestMetadata("EnumValuesConstructors.kt")
        fun testEnumValuesConstructors() {
            doCheck("uast-kotlin/testData/EnumValuesConstructors.kt", ::checkCallbackForEnumValuesConstructors)
        }

        @TestMetadata("EnumValueMembers.kt")
        fun testEnumValueMembers() {
            doCheck("uast-kotlin/testData/EnumValueMembers.kt", ::checkCallbackForEnumValueMembers)
        }

        @TestMetadata("SimpleAnnotated.kt")
        fun testSimpleAnnotated() {
            doCheck("uast-kotlin/testData/SimpleAnnotated.kt", ::checkCallbackForSimpleAnnotated)
        }

        @TestMetadata("SuperCalls.kt")
        fun testSuperCalls() {
            doCheck("uast-kotlin/testData/SuperCalls.kt", ::checkCallbackForSuperCalls)
        }

        @TestMetadata("Anonymous.kt")
        fun testAnonymous() {
            doCheck("uast-kotlin/testData/Anonymous.kt", ::checkCallbackForAnonymous)
        }

        @TestMetadata("TypeAliases.kt")
        fun testTypeAliases() {
            doCheck("uast-kotlin/testData/TypeAliases.kt", ::checkCallbackForTypeAliases)
        }

// TODO: vararg, arrayOf call inside annotation
//        @TestMetadata("AnnotationComplex.kt")
//        fun testAnnotationComplex() {
//            doCheck("uast-kotlin/testData/AnnotationComplex.kt", ::checkCallbackForAnnotationComplex)
//        }

// TODO: getArgumentsForParameter
//        @TestMetadata("ParametersDisorder.kt")
//        fun testParametersDisorder() {
//            doCheck("uast-kotlin/testData/ParametersDisorder.kt", ::checkCallbackForParametersDisorder)
//        }

// TODO: resolve to inline and stdlib
//        @TestMetadata("Resolve.kt")
//        fun testResolve() {
//            doCheck("uast-kotlin/testData/Resolve.kt", ::checkCallbackForResolve)
//        }

        @TestMetadata("Lambdas.kt")
        fun testLambdas() {
            doCheck("uast-kotlin/testData/Lambdas.kt", ::checkCallbackForLambdas)
        }

// TODO: resolve to local declarations/constructors
//        @TestMetadata("LocalDeclarations.kt")
//        fun testLocalDeclarations() {
//            doCheck("uast-kotlin/testData/LocalDeclarations.kt", ::checkCallbackForLocalDeclarations)
//        }

        @TestMetadata("Elvis.kt")
        fun testElvis() {
            doCheck("uast-kotlin/testData/Elvis.kt", ::checkCallbackForElvis)
        }

        @TestMetadata("TypeReferences.kt")
        fun testTypeReferences() {
            doCheck("uast-kotlin/testData/TypeReferences.kt", ::checkCallbackForTypeReferences)
        }

// TODO: return type of inline functions
//        @TestMetadata("ReifiedReturnType.kt")
//        fun testReifiedReturnType() {
//            doCheck("uast-kotlin/testData/ReifiedReturnType.kt", ::checkCallbackForReifiedReturnType)
//        }

        @TestMetadata("ReifiedParameters.kt")
        fun testReifiedParameters() {
          doCheck("uast-kotlin/testData/ReifiedParameters.kt", ::checkCallbackForReifiedParameters)
        }

        @TestMetadata("LambdaParameters.kt")
        fun testLambdaParameters() {
            doCheck("uast-kotlin/testData/LambdaParameters.kt", ::checkCallbackForLambdaParameters)
        }

        @TestMetadata("SAM.kt")
        fun testSAM() {
            doCheck("uast-kotlin/testData/SAM.kt", ::checkCallbackForSAM)
        }

        @TestMetadata("Simple.kt")
        fun testSimple() {
            doCheck("uast-kotlin/testData/Simple.kt", ::checkCallbackForSimple)
        }
    }
}
