// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.postfix

import com.jetbrains.python.allure.Components
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems

@Subsystems.CodeCompletion
@Components.Postfix
@Layers.Functional
class PyLenPostfixTemplateTest : PyPostfixTemplateTestCase() {

  fun testString() = doTest()

  fun testList() = doTest()

  fun testDict() = doTest()

  fun testTuple() = doTest()

  fun testSized() = doTest()

  fun testSizedClassObj() = doTest()

  fun testAsExpr() = doTest()

  fun testNotSized() = doTest()

  override fun getTestDataDir() = "len/"
}
