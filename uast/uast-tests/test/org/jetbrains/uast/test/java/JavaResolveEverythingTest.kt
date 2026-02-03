// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.test.java

import org.junit.Test

class JavaResolveEverythingTest : AbstractJavaResolveEverythingTest() {

  @Test
  fun testAnnotation() = doTest("Simple/Annotation.java")

  @Test
  fun testAnonymous() = doTest("Simple/Anonymous.java")

  @Test
  fun testAnonymousClassWithParameters() = doTest("Simple/AnonymousClassWithParameters.java")

  @Test
  fun testComments() = doTest("Simple/Comments.java")

  @Test
  fun testExternal() = doTest("Simple/External.java")

  @Test
  fun testTryWithResources() = doTest("Simple/TryWithResources.java")

  @Test
  fun testInnerClass() = doTest("Simple/InnerClass.java")

  @Test
  fun testComplexCalls() = doTest("Simple/ComplexCalls.java")

}