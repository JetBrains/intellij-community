// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.test.java

import com.intellij.platform.uast.testFramework.common.RenderLogTestBase
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiJavaFile
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UFile
import org.jetbrains.uast.visitor.UastVisitor
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

@TestApplication
class SimpleJavaRenderLogTest : RenderLogTestBase {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val uast by javaUastFixture(projectFixture) { testName, file -> check(testName, file) }

  override fun getTestDataPath(): String = uast.fixture.testDataPath

  override fun check(testName: String, file: UFile, checkParentConsistency: Boolean) {
    super.check(testName, file, checkParentConsistency)

    file.accept(object : UastVisitor {
      override fun visitElement(node: UElement): Boolean {
        if (node is PsiElement && node.isPhysical) {
          assertInstanceOf(PsiJavaFile::class.java, node.containingFile)
        }
        return false
      }
    })
  }

  @Test
  fun testComments(): Unit = uast.doTest("Simple/Comments.java")

  @Test
  fun testDataClass(): Unit = uast.doTest("DataClass/DataClass.java")

  @Test
  fun testEnumSwitch(): Unit = uast.doTest("Simple/EnumSwitch.java")

  @Test
  fun testEnhancedSwitch(): Unit = uast.doTest("Simple/EnhancedSwitch.java")

  @Test
  fun testLocalClass(): Unit = uast.doTest("Simple/LocalClass.java")

  @Test
  fun testReturnX(): Unit = uast.doTest("Simple/ReturnX.java")

  @Test
  fun testJava(): Unit = uast.doTest("Simple/Simple.java")

  @Test
  fun testClass(): Unit = uast.doTest("Simple/SuperTypes.java")

  @Test
  fun testTryWithResources(): Unit = uast.doTest("Simple/TryWithResources.java")

  @Test
  fun testEnumValueMembers(): Unit = uast.doTest("Simple/EnumValueMembers.java")

  @Test
  fun testQualifiedConstructorCall(): Unit = uast.doTest("Simple/QualifiedConstructorCall.java")

  @Test
  fun testAnonymousClassWithParameters(): Unit = uast.doTest("Simple/AnonymousClassWithParameters.java")

  @Test
  fun testVariableAnnotation(): Unit = uast.doTest("Simple/VariableAnnotation.java")

  @Test
  fun testPackageInfo(): Unit = uast.doTest("Simple/package-info.java")

  @Test
  fun testStrings(): Unit = uast.doTest("Simple/Strings.java")

  @Test
  fun testAnnotation(): Unit = uast.doTest("Simple/Annotation.java")

  @Test
  fun testComplexCalls(): Unit = uast.doTest("Simple/ComplexCalls.java")

  @Test
  fun testImports(): Unit = uast.doTest("Simple/External.java")

  @Test
  fun testLambda(): Unit = uast.doTest("Simple/Lambda.java")

  @Test
  fun testBlock(): Unit = uast.doTest("Simple/Block.java")

  @Test
  fun testTryCatch(): Unit = uast.doTest("Simple/TryCatch.java")
  
  @Test
  fun testRecord(): Unit = uast.doTest("Simple/Record.java")

  @Test
  fun testTypeUseAnnotations(): Unit = uast.doTest("Simple/TypeUseAnnotations.java")

  @Test
  fun testTypeAndConstructorAnnotations(): Unit = uast.doTest("Simple/TypeAndConstructorAnnotations.java")

  @Test
  fun testInstanceOfTypeTestPattern(): Unit = uast.doTest("Simple/InstanceOfTypeTestPattern.java")

  @Test
  fun testInstanceOfRecordPattern(): Unit = uast.doTest("Simple/InstanceOfRecordPattern.java")

  @Test
  fun testSwitchCaseTypeTestPattern(): Unit = uast.doTest("Simple/SwitchCaseTypeTestPattern.java")

  @Test
  fun testSwitchCaseRecordPattern(): Unit = uast.doTest("Simple/SwitchCaseRecordPattern.java")

  @Test
  fun testOldStyleSwitchStatementWithGuard(): Unit = uast.doTest("Simple/OldStyleSwitchStatementWithGuard.java")

  @Test
  fun testNewStyleSwitchStatementWithGuard(): Unit = uast.doTest("Simple/NewStyleSwitchStatementWithGuard.java")

  @Test
  fun testOldStyleSwitchExpressionWithGuard(): Unit = uast.doTest("Simple/OldStyleSwitchExpressionWithGuard.java")

  @Test
  fun testNewStyleSwitchExpressionWithGuard(): Unit = uast.doTest("Simple/NewStyleSwitchExpressionWithGuard.java")
}