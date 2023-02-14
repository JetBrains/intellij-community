// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.test.java

import org.jetbrains.uast.UFile
import org.jetbrains.uast.test.common.RenderLogTestBase
import org.junit.Test

class SimpleJavaRenderLogTest : AbstractJavaRenderLogTest(), RenderLogTestBase {

  override fun check(testName: String, file: UFile) {
    super<RenderLogTestBase>.check(testName, file)
  }

  @Test
  fun testComments() = doTest("Simple/Comments.java")

  @Test
  fun testDataClass() = doTest("DataClass/DataClass.java")

  @Test
  fun testEnumSwitch() = doTest("Simple/EnumSwitch.java")

  @Test
  fun testEnhancedSwitch() = doTest("Simple/EnhancedSwitch.java")

  @Test
  fun testLocalClass() = doTest("Simple/LocalClass.java")

  @Test
  fun testReturnX() = doTest("Simple/ReturnX.java")

  @Test
  fun testJava() = doTest("Simple/Simple.java")

  @Test
  fun testClass() = doTest("Simple/SuperTypes.java")

  @Test
  fun testTryWithResources() = doTest("Simple/TryWithResources.java")

  @Test
  fun testEnumValueMembers() = doTest("Simple/EnumValueMembers.java")

  @Test
  fun testQualifiedConstructorCall() = doTest("Simple/QualifiedConstructorCall.java")

  @Test
  fun testAnonymousClassWithParameters() = doTest("Simple/AnonymousClassWithParameters.java")

  @Test
  fun testVariableAnnotation() = doTest("Simple/VariableAnnotation.java")

  @Test
  fun testPackageInfo() = doTest("Simple/package-info.java")

  @Test
  fun testStrings() = doTest("Simple/Strings.java")

  @Test
  fun testAnnotation() = doTest("Simple/Annotation.java")

  @Test
  fun testComplexCalls() = doTest("Simple/ComplexCalls.java")

  @Test
  fun testImports() = doTest("Simple/External.java")

  @Test
  fun testLambda() = doTest("Simple/Lambda.java")

  @Test
  fun testBlock() = doTest("Simple/Block.java")

  @Test
  fun testTryCatch() = doTest("Simple/TryCatch.java")
  
  @Test
  fun testRecord() = doTest("Simple/Record.java")
}