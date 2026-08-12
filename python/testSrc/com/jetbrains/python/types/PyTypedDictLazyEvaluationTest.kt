// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.types

import com.intellij.idea.TestFor
import com.jetbrains.python.allure.Components
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyTestCase
import com.jetbrains.python.psi.PyAssignmentStatement
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.types.PyTypedDictType
import com.jetbrains.python.psi.types.TypeEvalContext

/**
 * What the laziness of [PyTypedDictType] is worth, asserted on observable state: whether an item
 * type has been evaluated in a context is visible through [TypeEvalContext.getKnownType].
 *
 * Every test here except [testItemsAreEvaluatedOnce], which guards the memoization the laziness needs to not cost anything,
 * fails on the eager implementation, where building the type of one TypedDict evaluated the item types of its whole hierarchy —
 * [testAskingForOneItemDoesNotEvaluateTheWholeHierarchy] counted 25 evaluated items instead of 3.
 */
@Subsystems.Typing
@Components.TypeInference
@Layers.Functional
@TestFor(issues = ["PY-85440"], classes = [PyTypedDictType::class])
class PyTypedDictLazyEvaluationTest : PyTestCase() {

  private companion object {
    const val DEPTH = 8
    const val FAN_OUT = 3
  }

  fun testItemTypesAreNotEvaluatedWhileTheTypeIsBuilt() {
    val file = configure("""
      from typing import TypedDict


      class Movie(TypedDict):
          name: str
          year: int


      def f(m: Movie):
          pass
      """.trimIndent())
    val context = TypeEvalContext.codeAnalysis(myFixture.project, file)
    val typedDictType = parameterTypeOf(file, "f", context)
    val items = itemsOf(file, "Movie")

    assertEmpty(items.filter { context.getKnownType(it) != null })

    assertEquals(setOf("name", "year"), typedDictType.fields.keys)
    assertEmpty(items.filter { context.getKnownType(it) == null })
  }

  fun testTheExtraItemsTypeIsNotEvaluatedWhileTheTypeIsBuilt() {
    val file = configure("""
      from typing_extensions import TypedDict


      class Extra(TypedDict, extra_items=int):
          name: str


      def f(e: Extra):
          pass
      """.trimIndent())
    val context = TypeEvalContext.codeAnalysis(myFixture.project, file)
    val typedDictType = parameterTypeOf(file, "f", context)
    val name = itemsOf(file, "Extra").single()

    assertNull(context.getKnownType(name))
    assertEquals("int", typedDictType.extraItemsType?.name)
    assertNull("Asking for the extra items type must not evaluate the declared items", context.getKnownType(name))
  }

  fun testOneTypeInstancePerDeclaration() {
    val references = (0 until 20).joinToString("\n\n") { "def f$it(m: Movie):\n    pass" }
    val file = configure("""
      from typing import TypedDict


      class Movie(TypedDict):
          name: str


      $references
      """.trimIndent())
    val context = TypeEvalContext.codeAnalysis(myFixture.project, file)

    val types = (0 until 20).map { parameterTypeOf(file, "f$it", context) }
    assertOneElement(types.distinctBy { System.identityHashCode(it) })
  }

  fun testTheSameNestedDeclarationIsNotRebuiltPerPath() {
    val file = configure(hierarchy() + """

      def f(t: T0):
          first = t["f0"]
          second = t["f1"]
      """.trimIndent())
    val context = TypeEvalContext.codeAnalysis(myFixture.project, file)

    val itemTypes = parameterTypeOf(file, "f", context).fields.values

    // Every item of T0 is a T1, and T1 is one type, not one per item: this is the DAG the eager implementation used to expand
    // into a tree of its own instances.
    assertOneElement(itemTypes.map { System.identityHashCode(it.type) }.distinct())
  }

  fun testItemsAreEvaluatedOnce() {
    val file = configure("""
      from typing import TypedDict


      class Movie(TypedDict):
          name: str


      def f(m: Movie):
          pass
      """.trimIndent())
    val context = TypeEvalContext.codeAnalysis(myFixture.project, file)
    val typedDictType = parameterTypeOf(file, "f", context)

    assertSame(typedDictType.fields, typedDictType.fields)
  }

  fun testAskingForOneItemDoesNotEvaluateTheWholeHierarchy() {
    val file = configure(hierarchy() + """

      def f(t: T0):
          expr = t["f0"]
      """.trimIndent())
    val context = TypeEvalContext.codeAnalysis(myFixture.project, file)

    val expr = file.findTopLevelFunction("f")!!.statementList.statements
      .filterIsInstance<PyAssignmentStatement>()
      .single()
      .assignedValue!!
    assertInstanceOf(context.getType(expr), PyTypedDictType::class.java)

    // Only the items of the TypedDict that was actually subscripted. Evaluating them yields the type of the next TypedDict in
    // the chain, and building that type does not evaluate its items, which is what keeps this from being DEPTH * FAN_OUT.
    assertEquals(FAN_OUT, evaluatedItemCount(file, context))
  }

  /** A chain of [DEPTH] TypedDicts, each declaring [FAN_OUT] items of the next one. */
  private fun hierarchy(): String {
    val declarations = (0 until DEPTH).joinToString("\n\n\n") { level ->
      val items = (0 until FAN_OUT).joinToString("\n") { "    f$it: \"T${level + 1}\"" }
      "class T$level(TypedDict):\n$items"
    }
    return "from typing import TypedDict\n\n\n$declarations\n\n\nclass T$DEPTH(TypedDict):\n    x: int\n"
  }

  private fun evaluatedItemCount(file: PyFile, context: TypeEvalContext): Int =
    file.topLevelClasses.sumOf { pyClass -> pyClass.classAttributes.count { context.getKnownType(it) != null } }

  private fun configure(text: String): PyFile {
    myFixture.configureByText("mod.py", text)
    return myFixture.file as PyFile
  }

  private fun parameterTypeOf(file: PyFile, functionName: String, context: TypeEvalContext): PyTypedDictType {
    val function = file.findTopLevelFunction(functionName) as PyFunction
    val parameter = function.parameterList.parameters.single().asNamed!!
    return assertInstanceOf(context.getType(parameter), PyTypedDictType::class.java)
  }

  private fun itemsOf(file: PyFile, className: String): List<PyTargetExpression> {
    val pyClass = file.findTopLevelClass(className) as PyClass
    return pyClass.classAttributes
  }
}
