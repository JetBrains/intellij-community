/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.uast.test.java

import org.junit.Test

class JavaIdentifiersTest : AbstractJavaIdentifiersTest() {

  @Test
  fun testAnnotation() = doTest("Simple/Annotation.java")

  @Test
  fun testAnonymous() = doTest("Simple/Anonymous.java")

  @Test
  fun testTryWithResources() = doTest("Simple/TryWithResources.java")

  @Test
  fun testInnerClass() = doTest("Simple/InnerClass.java")

  @Test
  fun testComplexCalls() = doTest("Simple/ComplexCalls.java")

}