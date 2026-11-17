// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.fir.uast.test

import com.intellij.testFramework.TestDataPath
import org.jetbrains.fir.uast.test.env.kotlin.AbstractFirUastTest
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.idea.test.JUnit3RunnerWithInners
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.uast.UFile
import org.jetbrains.uast.test.common.kotlin.UastApiTestBase
import org.junit.runner.RunWith
import java.nio.file.Path

@RunWith(JUnit3RunnerWithInners::class)
@TestRoot("uast")
abstract class FirUastApiTest : AbstractFirUastTest() {

    override val testBasePath: Path
        get() = testDataDirectory.toPath()
    override fun check(filePath: String, file: UFile) {
        // Bogus
    }

    private val whitelist : Set<String> = setOf(
    )

    override fun isExpectedToFail(filePath: String, fileContent: String): Boolean {
        return filePath in whitelist || super.isExpectedToFail(filePath, fileContent)
    }

    @TestMetadata("uast-kotlin-fir/tests/testData/declaration")
    @TestDataPath("\$PROJECT_ROOT")
    @RunWith(JUnit3RunnerWithInners::class)
    class Declaration : FirUastApiTest(), UastApiTestBase {
        @TestMetadata("retention.kt")
        fun testRetention() {
            doCheck("retention.kt", ::checkCallbackForRetention)
        }

        @TestMetadata("returns.kt")
        fun testReturnJumpTargets() {
            doCheck("returns.kt", ::checkReturnJumpTargets)
        }

        @TestMetadata("breakAndContinue.kt")
        fun testBreakAndContinueJumpTargets() {
            doCheck("breakAndContinue.kt", ::checkBreakAndContinueJumpTargets)
        }
    }

    @TestMetadata("uast-kotlin/tests/testData")
    @TestDataPath("\$PROJECT_ROOT")
    @RunWith(JUnit3RunnerWithInners::class)
    class Legacy : FirUastApiTest(), UastApiTestBase {
        @TestMetadata("AnnotationParameters.kt")
        fun testAnnotationParameters() {
            doCheck("AnnotationParameters.kt", ::checkCallbackForAnnotationParameters)
        }

        @TestMetadata("StringTemplateInClass.kt")
        fun testStringTemplateInClass() {
            doCheck("StringTemplateInClass.kt", ::checkCallbackForStringTemplateInClass)
        }

        @TestMetadata("StringTemplateWithVar.kt")
        fun testStringTemplateWithVar() {
            doCheck("StringTemplateWithVar.kt", ::checkCallbackForStringTemplateWithVar)
        }

        @TestMetadata("NameContainingFile.kt")
        fun testNameContainingFile() {
            doCheck("NameContainingFile.kt", ::checkCallbackForNameContainingFile)
        }

        @TestMetadata("DefaultImpls.kt")
        fun testDefaultImpls() {
            doCheck("DefaultImpls.kt", ::checkCallbackForDefaultImpls)
        }

        @TestMetadata("ParameterPropertyWithAnnotation.kt")
        fun testParameterPropertyWithAnnotation() {
            doCheck("ParameterPropertyWithAnnotation.kt", ::checkCallbackForParameterPropertyWithAnnotation)
        }

        @TestMetadata("TypeInAnnotation.kt")
        fun testTypeInAnnotation() {
            doCheck("TypeInAnnotation.kt", ::checkCallbackForTypeInAnnotation)
        }

        @TestMetadata("ElvisType.kt")
        fun testElvisType() {
            doCheck("ElvisType.kt", ::checkCallbackForElvisType)
        }

        @TestMetadata("IfStatement.kt")
        fun testIfStatement() {
            doCheck("IfStatement.kt", ::checkCallbackForIfStatement)
        }

        @TestMetadata("WhenStringLiteral.kt")
        fun testWhenStringLiteral() {
            doCheck("WhenStringLiteral.kt", ::checkCallbackForWhenStringLiteral)
        }

        @TestMetadata("WhenAndDestructing.kt")
        fun testWhenAndDestructing() {
            doCheck("WhenAndDestructing.kt", ::checkCallbackForWhenAndDestructing)
        }

        @TestMetadata("BrokenMethod.kt")
        fun testBrokenMethod() {
            doCheck("BrokenMethod.kt", ::checkCallbackForBrokenMethod)
        }

        @TestMetadata("EnumValuesConstructors.kt")
        fun testEnumValuesConstructors() {
            doCheck("EnumValuesConstructors.kt", ::checkCallbackForEnumValuesConstructors)
        }

        @TestMetadata("EnumValueMembers.kt")
        fun testEnumValueMembers() {
            doCheck("EnumValueMembers.kt", ::checkCallbackForEnumValueMembers)
        }

        @TestMetadata("SuperCalls.kt")
        fun testSuperCalls() {
            doCheck("SuperCalls.kt", ::checkCallbackForSuperCalls)
        }

        @TestMetadata("Anonymous.kt")
        fun testAnonymous() {
            doCheck("Anonymous.kt", ::checkCallbackForAnonymous)
        }

        @TestMetadata("TypeAliases.kt")
        fun testTypeAliases() {
            doCheck("TypeAliases.kt", ::checkCallbackForTypeAliases)
        }

        @TestMetadata("AnnotationComplex.kt")
        fun testAnnotationComplex() {
            doCheck("AnnotationComplex.kt", ::checkCallbackForAnnotationComplex)
        }

        @TestMetadata("ParametersDisorder.kt")
        fun testParametersDisorder() {
            doCheck("ParametersDisorder.kt", ::checkCallbackForParametersDisorder)
        }

        @TestMetadata("Lambdas.kt")
        fun testLambdas() {
            doCheck("Lambdas.kt", ::checkCallbackForLambdas)
        }

        @TestMetadata("LocalDeclarations.kt")
        fun testLocalDeclarations() {
            doCheck("LocalDeclarations.kt", ::checkCallbackForLocalDeclarations)
        }

        @TestMetadata("Elvis.kt")
        fun testElvis() {
            doCheck("Elvis.kt", ::checkCallbackForElvis)
        }

        @TestMetadata("TypeReferences.kt")
        fun testTypeReferences() {
            doCheck("TypeReferences.kt", ::checkCallbackForTypeReferences)
        }

        @TestMetadata("ReifiedReturnType.kt")
        fun testReifiedReturnType() {
            doCheck("ReifiedReturnType.kt", ::checkCallbackForReifiedReturnType)
        }

        @TestMetadata("ReifiedParameters.kt")
        fun testReifiedParameters() {
          doCheck("ReifiedParameters.kt", ::checkCallbackForReifiedParameters)
        }

        @TestMetadata("LambdaParameters.kt")
        fun testLambdaParameters() {
            doCheck("LambdaParameters.kt", ::checkCallbackForLambdaParameters)
        }

        @TestMetadata("ComplicatedTypes.kt")
        fun testComplicatedTypes() {
            doCheck("ComplicatedTypes.kt", ::checkCallbackForComplicatedTypes)
        }

        @TestMetadata("SAM.kt")
        fun testSAM() {
            doCheck("SAM.kt") { uFilePath, uFile ->
                checkCallbackForSAM(uFilePath, uFile, "java.lang.String", "java.lang.String")
            }
        }

        @TestMetadata("Simple.kt")
        fun testSimple() {
            doCheck("Simple.kt", ::checkCallbackForSimple)
        }

        @TestMetadata("StringTemplateComplexForUInjectionHost.kt")
        fun testStringTemplateComplexForUInjectionHost() {
            doCheck("StringTemplateComplexForUInjectionHost.kt", ::checkCallbackForComplexStrings)
        }

        @TestMetadata("WhenIs.kt")
        fun testWhenIs() {
            doCheck("WhenIs.kt", ::checkSwitchYieldTargets)
        }
    }
}
