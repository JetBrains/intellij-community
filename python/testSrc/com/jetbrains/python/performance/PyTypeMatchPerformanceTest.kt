// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.performance

import com.intellij.idea.TestFor
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.testFramework.PerformanceUnitTest
import com.jetbrains.python.allure.Components
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.psi.types.PyAnyType
import com.jetbrains.python.psi.types.PyCallableParameterImpl
import com.jetbrains.python.psi.types.PyCallableTypeImpl
import com.jetbrains.python.psi.types.PyTupleType
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyTypeChecker
import com.jetbrains.python.psi.types.PyTypeVarTypeImpl
import com.jetbrains.python.psi.types.TypeEvalContext
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * Measures [PyTypeChecker.match] and [PyTypeChecker.substitute] on constructed types: callables nested as a directed
 * acyclic graph, tuples nested the same way, and a union of literal types.
 *
 * Each subtype of a nested type is shared by two parents. The type has few distinct nodes and many paths. A structural
 * `hashCode` or `equals` that does not memoize the shared nodes walks every path, so its work doubles for each level.
 * The two literal parameters have equal types. They are two objects, so the match cannot stop at the identity check.
 */
@PerformanceUnitTest
@Subsystems.Typing
@Components.TypeInference
@Layers.Performance
@TestFor(classes = [PyTypeChecker::class, PyCallableTypeImpl::class, PyTupleType::class, PyCallableParameterImpl::class])
class PyTypeMatchPerformanceTest : PyCodeInsightTestCase() {

  @JvmField
  @RegisterExtension
  val perf = PyPerfProbeExtension { myFixture }

  @Test
  fun `nested callables benchmark`() {
    val (file, int) = configureEmpty()
    val depth = 10
    measureMatch("callable_equal/depth=$depth", file, nestedCallable(depth, int), nestedCallable(depth, int))
    val typeVar = PyTypeVarTypeImpl("T", PyAnyType.unknown)
    val generic = nestedCallable(8, typeVar)
    val substitutions = PyTypeChecker.GenericSubstitutions(mapOf(typeVar to int))
    perf.probe.report(perf.probe.measure("callable_substitute/depth=8", setup = {}) {
      runReadActionBlocking {
        val context = TypeEvalContext.codeInsightFallback(file.project)
        repeat(REPEATS) { PyTypeChecker.substitute(generic, substitutions, context) }
      }
    })
  }

  @Test
  fun `nested tuples benchmark`() {
    val (file, int) = configureEmpty()
    measureMatch("tuple_equal/depth=14", file, nestedTuple(file, 14, int), nestedTuple(file, 14, int))
  }

  @Test
  fun `literal union benchmark`() {
    val (file, types) = configureLiteralUnion(200)
    measureMatch("literal_union/n=200", file, types.first, types.second)
  }

  private fun configureEmpty(): Pair<PyFile, PyType> {
    val file = myFixture.configureByText("a.py", "") as PyFile
    return file to runReadActionBlocking { PyBuiltinCache.getInstance(file).intType!! }
  }

  /** `def f(a: Literal[...], b: Literal[...])` with the same [literals] strings, and the types of `a` and `b`. */
  private fun configureLiteralUnion(literals: Int): Pair<PyFile, Pair<PyType, PyType>> {
    val values = (0 until literals).joinToString(", ") { "\"v$it\"" }
    val file = myFixture.configureByText("union_$literals.py", """
      from typing import Literal
      def f(a: Literal[$values], b: Literal[$values]): ...
    """.trimIndent()) as PyFile
    val types = runReadActionBlocking {
      val parameters = file.findTopLevelFunction("f")!!.parameterList.parameters
      val context = TypeEvalContext.codeAnalysis(file.project, file)
      context.getType(parameters[0].asNamed!!)!! to context.getType(parameters[1].asNamed!!)!!
    }
    assertNotSame(types.first, types.second)
    return file to types
  }

  private fun measureMatch(scenario: String, file: PyFile, expected: PyType, actual: PyType) {
    perf.probe.report(perf.probe.measure(scenario, setup = {}) {
      runReadActionBlocking {
        val context = TypeEvalContext.codeInsightFallback(file.project)
        repeat(REPEATS) { assertTrue(PyTypeChecker.match(expected, actual, context)) }
      }
    })
  }

  /** `C0 = leaf`, `Ck = Callable[[Ck-1, Ck-1], Ck-1]`. */
  private fun nestedCallable(depth: Int, leaf: PyType): PyType {
    var type = leaf
    repeat(depth) {
      val parameters = listOf(PyCallableParameterImpl.nonPsi(type), PyCallableParameterImpl.nonPsi(type))
      type = PyCallableTypeImpl(parameters, type)
    }
    return type
  }

  /** `T0 = leaf`, `Tk = tuple[Tk-1, Tk-1]`. */
  private fun nestedTuple(file: PyFile, depth: Int, leaf: PyType): PyType = runReadActionBlocking {
    var type = leaf
    repeat(depth) { type = PyTupleType.create(file, listOf(type, type))!! }
    type
  }

  private companion object {
    const val REPEATS = 100
  }
}
