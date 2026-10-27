// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.intellij.idea.TestFor
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyTestCase
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.types.PyCallableType
import com.jetbrains.python.psi.types.PyCloningTypeVisitor
import com.jetbrains.python.psi.types.TypeEvalContext

@Subsystems.Typing
@Layers.Functional
@TestFor(classes = [PyCloningTypeVisitor::class])
class PyCloningTypeVisitorTest : PyTestCase() {
  fun testCloningCallableParametersDoesNotLoadDefaultValueAst() {
    val module = myFixture.addFileToProject("mod.py", "def f(x: int = 1) -> int: ...") as PyFile
    val origin = myFixture.configureByText("a.py", "")

    val context = TypeEvalContext.codeAnalysis(myFixture.project, origin)
    val function = module.findTopLevelFunction("f")!!
    val functionType = context.getType(function) as PyCallableType
    val cloner = object : PyCloningTypeVisitor(context) {}
    val clonedType = PyCloningTypeVisitor.clone(functionType, cloner) as PyCallableType

    assertEquals("1", clonedType.getParameters(context)!!.single().defaultValueText)
    assertNotParsed(module)
  }
}
