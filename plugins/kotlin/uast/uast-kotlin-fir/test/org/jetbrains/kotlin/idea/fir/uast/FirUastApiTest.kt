// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.uast

import com.intellij.testFramework.TestDataPath
import org.jetbrains.kotlin.idea.base.test.KotlinRoot
import org.jetbrains.kotlin.idea.fir.uast.env.kotlin.AbstractFirUastTest
import org.jetbrains.kotlin.idea.test.JUnit3RunnerWithInners
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.uast.UFile
import org.jetbrains.uast.test.common.kotlin.UastApiTestBase
import org.junit.runner.RunWith
import java.nio.file.Path

@RunWith(JUnit3RunnerWithInners::class)
abstract class FirUastApiTest : AbstractFirUastTest() {
    override val isFirUastPlugin: Boolean = true
    override val testBasePath: Path = KotlinRoot.PATH.resolve("uast")
    override fun check(filePath: String, file: UFile) {
        // Bogus
    }

    private val whitelist : Set<String> = setOf(
    )

    override fun isExpectedToFail(filePath: String, fileContent: String): Boolean {
        return filePath in whitelist || super.isExpectedToFail(filePath, fileContent)
    }

    @TestMetadata("plugins/uast-kotlin-fir/testData/declaration")
    @TestDataPath("\$PROJECT_ROOT")
    @RunWith(JUnit3RunnerWithInners::class)
    class Declaration : FirUastApiTest(), UastApiTestBase {
        @TestMetadata("retention.kt")
        fun testRetention() {
            doCheck("uast-kotlin-fir/testData/declaration/retention.kt", ::checkCallbackForRetention)
        }

        @TestMetadata("returns.kt")
        fun testReturnJumpTargets() {
            doCheck("uast-kotlin-fir/testData/declaration/returns.kt", ::checkReturnJumpTargets)
        }
    }

    @TestMetadata("../uast-kotlin/tests/testData")
    @TestDataPath("\$PROJECT_ROOT")
    @RunWith(JUnit3RunnerWithInners::class)
    class Legacy : FirUastApiTest(), UastApiTestBase {
        @TestMetadata("AnnotationParameters.kt")
        fun testAnnotationParameters() {
            doCheck("uast-kotlin/tests/testData/AnnotationParameters.kt", ::checkCallbackForAnnotationParameters)
        }

        @TestMetadata("StringTemplateInClass.kt")
        fun testStringTemplateInClass() {
            doCheck("uast-kotlin/tests/testData/StringTemplateInClass.kt", ::checkCallbackForStringTemplateInClass)
        }

        @TestMetadata("StringTemplateWithVar.kt")
        fun testStringTemplateWithVar() {
            doCheck("uast-kotlin/tests/testData/StringTemplateWithVar.kt", ::checkCallbackForStringTemplateWithVar)
        }

        @TestMetadata("NameContainingFile.kt")
        fun testNameContainingFile() {
            doCheck("uast-kotlin/tests/testData/NameContainingFile.kt", ::checkCallbackForNameContainingFile)
        }

        @TestMetadata("DefaultImpls.kt")
        fun testDefaultImpls() {
            doCheck("uast-kotlin/tests/testData/DefaultImpls.kt", ::checkCallbackForDefaultImpls)
        }

        @TestMetadata("ParameterPropertyWithAnnotation.kt")
        fun testParameterPropertyWithAnnotation() {
            doCheck("uast-kotlin/tests/testData/ParameterPropertyWithAnnotation.kt", ::checkCallbackForParameterPropertyWithAnnotation)
        }

        @TestMetadata("TypeInAnnotation.kt")
        fun testTypeInAnnotation() {
            doCheck("uast-kotlin/tests/testData/TypeInAnnotation.kt", ::checkCallbackForTypeInAnnotation)
        }

        @TestMetadata("ElvisType.kt")
        fun testElvisType() {
            doCheck("uast-kotlin/tests/testData/ElvisType.kt", ::checkCallbackForElvisType)
        }

        @TestMetadata("IfStatement.kt")
        fun testIfStatement() {
            doCheck("uast-kotlin/tests/testData/IfStatement.kt", ::checkCallbackForIfStatement)
        }

        @TestMetadata("WhenStringLiteral.kt")
        fun testWhenStringLiteral() {
            doCheck("uast-kotlin/tests/testData/WhenStringLiteral.kt", ::checkCallbackForWhenStringLiteral)
        }

        @TestMetadata("WhenAndDestructing.kt")
        fun testWhenAndDestructing() {
            doCheck("uast-kotlin/tests/testData/WhenAndDestructing.kt", ::checkCallbackForWhenAndDestructing)
        }

        @TestMetadata("BrokenMethod.kt")
        fun testBrokenMethod() {
            doCheck("uast-kotlin/tests/testData/BrokenMethod.kt", ::checkCallbackForBrokenMethod)
        }

        @TestMetadata("EnumValuesConstructors.kt")
        fun testEnumValuesConstructors() {
            doCheck("uast-kotlin/tests/testData/EnumValuesConstructors.kt", ::checkCallbackForEnumValuesConstructors)
        }

        @TestMetadata("EnumValueMembers.kt")
        fun testEnumValueMembers() {
            doCheck("uast-kotlin/tests/testData/EnumValueMembers.kt", ::checkCallbackForEnumValueMembers)
        }

        @TestMetadata("SimpleAnnotated.kt")
        fun testSimpleAnnotated() {
            doCheck("uast-kotlin/tests/testData/SimpleAnnotated.kt", ::checkCallbackForSimpleAnnotated)
        }

        @TestMetadata("SuperCalls.kt")
        fun testSuperCalls() {
            doCheck("uast-kotlin/tests/testData/SuperCalls.kt", ::checkCallbackForSuperCalls)
        }

        @TestMetadata("Anonymous.kt")
        fun testAnonymous() {
            doCheck("uast-kotlin/tests/testData/Anonymous.kt", ::checkCallbackForAnonymous)
        }

        @TestMetadata("TypeAliases.kt")
        fun testTypeAliases() {
            doCheck("uast-kotlin/tests/testData/TypeAliases.kt", ::checkCallbackForTypeAliases)
        }

        @TestMetadata("AnnotationComplex.kt")
        fun testAnnotationComplex() {
            doCheck("uast-kotlin/tests/testData/AnnotationComplex.kt", ::checkCallbackForAnnotationComplex)
        }

        @TestMetadata("ParametersDisorder.kt")
        fun testParametersDisorder() {
            doCheck("uast-kotlin/tests/testData/ParametersDisorder.kt", ::checkCallbackForParametersDisorder)
        }

        @TestMetadata("Lambdas.kt")
        fun testLambdas() {
            doCheck("uast-kotlin/tests/testData/Lambdas.kt", ::checkCallbackForLambdas)
        }

        @TestMetadata("LocalDeclarations.kt")
        fun testLocalDeclarations() {
            doCheck("uast-kotlin/tests/testData/LocalDeclarations.kt", ::checkCallbackForLocalDeclarations)
        }

        @TestMetadata("Elvis.kt")
        fun testElvis() {
            doCheck("uast-kotlin/tests/testData/Elvis.kt", ::checkCallbackForElvis)
        }

        @TestMetadata("TypeReferences.kt")
        fun testTypeReferences() {
            doCheck("uast-kotlin/tests/testData/TypeReferences.kt", ::checkCallbackForTypeReferences)
        }

        @TestMetadata("ReifiedReturnType.kt")
        fun testReifiedReturnType() {
            doCheck("uast-kotlin/tests/testData/ReifiedReturnType.kt", ::checkCallbackForReifiedReturnType)
        }

        @TestMetadata("ReifiedParameters.kt")
        fun testReifiedParameters() {
          doCheck("uast-kotlin/tests/testData/ReifiedParameters.kt", ::checkCallbackForReifiedParameters)
        }

        @TestMetadata("LambdaParameters.kt")
        fun testLambdaParameters() {
            doCheck("uast-kotlin/tests/testData/LambdaParameters.kt", ::checkCallbackForLambdaParameters)
        }

        @TestMetadata("SAM.kt")
        fun testSAM() {
            doCheck("uast-kotlin/tests/testData/SAM.kt", ::checkCallbackForSAM)
        }

        @TestMetadata("Simple.kt")
        fun testSimple() {
            doCheck("uast-kotlin/tests/testData/Simple.kt", ::checkCallbackForSimple)
        }

        @TestMetadata("StringTemplateComplexForUInjectionHost.kt")
        fun testStringTemplateComplexForUInjectionHost() {
            doCheck("uast-kotlin/tests/testData/StringTemplateComplexForUInjectionHost.kt", ::checkCallbackForComplexStrings)
        }

        @TestMetadata("WhenIs.kt")
        fun testWhenIs() {
            doCheck("uast-kotlin/tests/testData/WhenIs.kt", ::checkSwitchYieldTargets)
        }
    }
}
