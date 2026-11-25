// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.types

import com.intellij.idea.TestFor
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import com.jetbrains.python.allure.Components
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.psi.AccessDirection
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.resolve.RatedResolveResult
import com.jetbrains.python.psi.types.PyCallableParameterImpl
import com.jetbrains.python.psi.types.PyCallableTypeImpl
import com.jetbrains.python.psi.types.PyTupleType
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyUnpackedTupleTypeImpl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Test

/**
 * The callable, tuple, unpacked tuple and callable parameter types compute their hash code once.
 *
 * A [CountingLeaf] counts the calls of its `hashCode`. Each level of a nested type uses the type of the level below it
 * more than once. So a hash code that is not memoized calls the leaf an exponential number of times. A memoized one
 * calls it once for each place where the innermost type uses the leaf.
 */
@Subsystems.Typing
@Components.TypeInference
@Layers.Functional
@TestFor(issues = ["PY-94432"],
         classes = [PyCallableTypeImpl::class, PyTupleType::class, PyUnpackedTupleTypeImpl::class, PyCallableParameterImpl::class])
class PyMemoizedTypeHashCodeTest : PyCodeInsightTestCase() {

  private var leafHashCodeCalls = 0

  @Test
  fun `the hash code of a nested callable visits each nested type once`() {
    val callable = nestedCallable(DEPTH, CountingLeaf("A"))
    assertEquals(3, leafHashCodeCallsOf(callable))
    assertEquals(0, leafHashCodeCallsOf(callable))
  }

  @Test
  fun `the hash code of a nested tuple visits each nested type once`() {
    val tuple = nestedTuple(configureEmpty(), DEPTH, CountingLeaf("A"))
    assertEquals(2, leafHashCodeCallsOf(tuple))
    assertEquals(0, leafHashCodeCallsOf(tuple))
  }

  @Test
  fun `a second hash code of an unpacked tuple does not visit the element types`() {
    val unpackedTuple = PyUnpackedTupleTypeImpl.create(listOf(CountingLeaf("A"), CountingLeaf("B")))
    assertEquals(2, leafHashCodeCallsOf(unpackedTuple))
    assertEquals(0, leafHashCodeCallsOf(unpackedTuple))
  }

  @Test
  fun `a second hash code of a callable parameter does not visit the parameter type`() {
    val parameter = PyCallableParameterImpl.nonPsi("p", CountingLeaf("A"))
    assertEquals(1, leafHashCodeCallsOf(parameter))
    assertEquals(0, leafHashCodeCallsOf(parameter))
  }

  @Test
  fun `equal nested types have equal hash codes`() {
    val file = configureEmpty()
    val pairs = listOf(
      nestedCallable(DEPTH, CountingLeaf("A")) to nestedCallable(DEPTH, CountingLeaf("A")),
      nestedTuple(file, DEPTH, CountingLeaf("A")) to nestedTuple(file, DEPTH, CountingLeaf("A")),
      PyUnpackedTupleTypeImpl.create(listOf(CountingLeaf("A"))) to PyUnpackedTupleTypeImpl.create(listOf(CountingLeaf("A"))),
      PyUnpackedTupleTypeImpl.createUnbound(CountingLeaf("A")) to PyUnpackedTupleTypeImpl.createUnbound(CountingLeaf("A")),
      PyCallableParameterImpl.nonPsi("p", CountingLeaf("A")) to PyCallableParameterImpl.nonPsi("p", CountingLeaf("A")),
    )
    for ((first, second) in pairs) {
      assertNotSame(first, second)
      assertEquals(first, second)
      val firstHashCode = first.hashCode()
      assertEquals(firstHashCode, first.hashCode())
      assertEquals(firstHashCode, second.hashCode())
    }
  }

  private fun leafHashCodeCallsOf(value: Any): Int {
    leafHashCodeCalls = 0
    val _ = value.hashCode()
    return leafHashCodeCalls
  }

  private fun configureEmpty(): PyFile = myFixture.configureByText("a.py", "") as PyFile

  /** `C0 = leaf`, `Ck = Callable[[Ck-1, Ck-1], Ck-1]`. */
  private fun nestedCallable(depth: Int, leaf: PyType): PyType {
    var type = leaf
    repeat(depth) {
      type = PyCallableTypeImpl(listOf(PyCallableParameterImpl.nonPsi(type), PyCallableParameterImpl.nonPsi(type)), type)
    }
    return type
  }

  /** `T0 = leaf`, `Tk = tuple[Tk-1, Tk-1]`. */
  private fun nestedTuple(file: PyFile, depth: Int, leaf: PyType): PyType = runReadActionBlocking {
    var type = leaf
    repeat(depth) { type = PyTupleType.create(file, listOf(type, type))!! }
    type
  }

  private inner class CountingLeaf(private val tag: String) : PyType {
    override fun hashCode(): Int {
      leafHashCodeCalls++
      return tag.hashCode()
    }

    override fun equals(other: Any?): Boolean = other is CountingLeaf && other.tag == tag
    override fun resolveMember(name: String, location: PyExpression?, direction: AccessDirection, resolveContext: PyResolveContext): List<RatedResolveResult>? = null
    override fun getCompletionVariants(completionPrefix: String?, location: PsiElement, context: ProcessingContext): Array<out Any> = emptyArray()
    override val name: String get() = tag
    override val isBuiltin: Boolean get() = false
    override fun assertValid(message: String?) {}
  }

  private companion object {
    const val DEPTH = 12
  }
}
