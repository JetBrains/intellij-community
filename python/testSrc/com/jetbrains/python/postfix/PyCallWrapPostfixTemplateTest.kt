// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.postfix

import com.jetbrains.python.allure.Components
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems

@Subsystems.CodeCompletion
@Components.Postfix
@Layers.Functional
class PyCallWrapPostfixTemplateTest : PyPostfixTemplateTestCase() {

  fun testStr() = doTest("x = 1\nx.str<caret>", "x = 1\nstr(x)")

  fun testList() = doTest("x = []\nx.list<caret>", "x = []\nlist(x)")

  fun testSet() = doTest("x = []\nx.set<caret>", "x = []\nset(x)")

  fun testDict() = doTest("x = []\nx.dict<caret>", "x = []\ndict(x)")

  fun testTuple() = doTest("x = []\nx.tuple<caret>", "x = []\ntuple(x)")

  override fun getTestDataDir() = ""
}
