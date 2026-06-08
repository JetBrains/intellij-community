// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.fir.uast.test

import com.intellij.platform.uast.testFramework.common.PossibleSourceTypesTestBase
import com.intellij.platform.uast.testFramework.common.allUElementSubtypes
import org.jetbrains.fir.uast.test.env.kotlin.AbstractFirUastTest
import org.jetbrains.uast.UCallableReferenceExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UField
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.test.kotlin.TEST_KOTLIN_MODEL_PATH
import org.junit.Test
import java.nio.file.Path

class KotlinPossibleSourceTypesTest : AbstractFirUastTest(), PossibleSourceTypesTestBase {

    override val testBasePath: Path = TEST_KOTLIN_MODEL_PATH

    override fun check(filePath: String, file: UFile) {
        val psiFile = file.sourcePsi
        for (uastType in allUElementSubtypes) {
            checkConsistencyWithRequiredTypes(psiFile, uastType)
        }
        checkConsistencyWithRequiredTypes(psiFile, UClass::class.java, UMethod::class.java, UField::class.java)
        checkConsistencyWithRequiredTypes(
            psiFile,
            USimpleNameReferenceExpression::class.java,
            UQualifiedReferenceExpression::class.java,
            UCallableReferenceExpression::class.java
        )
    }

    @Test
    fun testAnnotationComplex() = doCheck("AnnotationComplex.kt")

    @Test
    fun testAnnotationParameters() = doCheck("AnnotationParameters.kt")

    // KTIJ-38551
    fun _testAnonymous() = doCheck("Anonymous.kt")

    // KTIJ-38551
    fun _testBitwise() = doCheck("Bitwise.kt")

    @Test
    fun testClassAnnotation() = doCheck("ClassAnnotation.kt")

    // KTIJ-38551
    fun _testConstructors() = doCheck("Constructors.kt")

    @Test
    fun testConstructorDelegate() = doCheck("ConstructorDelegate.kt")

    @Test
    fun testDefaultImpls() = doCheck("DefaultImpls.kt")

    @Test
    fun testDefaultParameterValues() = doCheck("DefaultParameterValues.kt")

    @Test
    fun testDelegate() = doCheck("Delegate.kt")

    @Test
    fun testDeprecatedHidden() = doCheck("DeprecatedHidden.kt")

    // KTIJ-38551
    fun _testDestructuringDeclaration() = doCheck("DestructuringDeclaration.kt")

    @Test
    fun testElvis() = doCheck("Elvis.kt")

    @Test
    fun testEnumValueMembers() = doCheck("EnumValueMembers.kt")

    @Test
    fun testEnumValuesConstructors() = doCheck("EnumValuesConstructors.kt")

    // KTIJ-38551
    fun _testIfStatement() = doCheck("IfStatement.kt")

    @Test
    fun testInnerClasses() = doCheck("InnerClasses.kt")

    // KTIJ-38551
    fun _testLambdaReturn() = doCheck("LambdaReturn.kt")

    // KTIJ-38551
    fun _testLambdas() = doCheck("Lambdas.kt")

    // KTIJ-38551
    fun _testLocalDeclarations() = doCheck("LocalDeclarations.kt")

    // KTIJ-38551
    fun _testLocalVariableWithAnnotation() = doCheck("LocalVariableWithAnnotation.kt")

    @Test
    fun testParameterPropertyWithAnnotation() = doCheck("ParameterPropertyWithAnnotation.kt")

    @Test
    fun testParametersWithDefaultValues() = doCheck("ParametersWithDefaultValues.kt")

    @Test
    fun testParametersDisorder() = doCheck("ParametersDisorder.kt")

    @Test
    fun testPropertyAccessors() = doCheck("PropertyAccessors.kt")

    @Test
    fun testPropertyInitializer() = doCheck("PropertyInitializer.kt")

    @Test
    fun testPropertyInitializerWithoutSetter() = doCheck("PropertyInitializerWithoutSetter.kt")

    @Test
    fun testPropertyWithAnnotation() = doCheck("PropertyWithAnnotation.kt")

    @Test
    fun testReceiverFun() = doCheck("ReceiverFun.kt")

    @Test
    fun testReified() = doCheck("Reified.kt")

    @Test
    fun testReifiedParameters() = doCheck("ReifiedParameters.kt")

    @Test
    fun testReifiedReturnType() = doCheck("ReifiedReturnType.kt")

    @Test
    fun testQualifiedConstructorCall() = doCheck("QualifiedConstructorCall.kt")

    @Test
    fun testSimple() = doCheck("Simple.kt")

    @Test
    fun testStringTemplate() = doCheck("StringTemplate.kt")

    // KTIJ-38551
    fun _testStringTemplateComplex() = doCheck("StringTemplateComplex.kt")

    // KTIJ-38551
    fun _testStringTemplateComplexForUInjectionHost() = doCheck("StringTemplateComplexForUInjectionHost.kt")

    @Test
    fun testSuperCalls() = doCheck("SuperCalls.kt")

    @Test
    fun testSuspend() = doCheck("Suspend.kt")

    @Test
    fun testTryCatch() = doCheck("TryCatch.kt")

    // KTIJ-38551
    fun _testTypeReferences() = doCheck("TypeReferences.kt")

    @Test
    fun testUnexpectedContainer() = doCheck("UnexpectedContainerException.kt")

    // KTIJ-38551
    fun _testWhenAndDestructing() = doCheck("WhenAndDestructing.kt")

    // KTIJ-38551
    fun _testWhenIs() = doCheck("WhenIs.kt")

    @Test
    fun testWhenStringLiteral() = doCheck("WhenStringLiteral.kt")
}