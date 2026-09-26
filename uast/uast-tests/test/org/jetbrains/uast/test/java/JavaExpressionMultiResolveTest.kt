// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.test.java

import org.junit.Test

class JavaExpressionMultiResolveTest : AbstractJavaExpressionMultiResolveTest() {
  @Test
  fun testConstructorCallWithParameters() = doTest("Simple/ConstructorCallWithParameters.java") // must resolve to PsiMethodImpl

  @Test
  fun testDefaultConstructorCall() = doTest("Simple/DefaultConstructorCall.java") // must resolve to PsiClassImpl
}