// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.types

import com.intellij.idea.TestFor
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import com.jetbrains.python.allure.Components
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.psi.AccessDirection
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.resolve.RatedResolveResult
import com.jetbrains.python.psi.types.PyClassTypeImpl
import com.jetbrains.python.psi.types.PyCompositeTypeBase
import com.jetbrains.python.psi.types.PyIntersectionType
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyUnionType
import com.jetbrains.python.psi.types.PyUnsafeUnionType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong

/**
 * Guards [PyCompositeTypeBase]'s memoized `hashCode` and fast-path `equals` against the structural-equality
 * storm on deeply nested composite types (unions, unsafe unions, intersections) that froze the UI (PY-90901).
 *
 * How it works: the fake [CountingLeaf]/[Wrapper] types tally every `hashCode`/`equals` call they receive.
 * [buildSharedDag] builds a DAG (Directed Acyclic Graph), not a tree: each level wraps the *same* child in two
 * distinct wrappers, so the root has only `depth` distinct nodes yet is reachable via 2^depth paths. A naive
 * `hashCode` re-walks every path — O(2^depth), ~2^23 calls at [DEPTH] = 22 — while a memoized one visits each
 * node once — O(depth). The tests assert the counter stays under [LINEAR_BOUND]: the O(depth) fix clears it with
 * a huge margin, the O(2^depth) original blows past it.
 * Assertions use this deterministic counter and run against every composite kind.
 */
@Layers.Functional
@TestFor(issues = ["PY-90901", "PY-89956"],
         classes = [PyCompositeTypeBase::class, PyUnionType::class, PyUnsafeUnionType::class, PyIntersectionType::class, PyClassTypeImpl::class])
class PyCompositeTypeEqualityPerformanceTest : PyCodeInsightTestCase() {

  private val counter = AtomicLong()

  private val composites: List<Pair<String, (List<PyType?>) -> PyType>> = listOf(
    "PyUnionType" to { members -> PyUnionType.union(members)!! },
    "PyUnsafeUnionType" to { members -> PyUnsafeUnionType.unsafeUnion(members)!! },
    "PyIntersectionType" to { members -> PyIntersectionType.intersection(members)!! },
  )

  /** Leaf type with stable identity; counts every `hashCode`/`equals` it receives. */
  private inner class CountingLeaf(private val tag: String) : PyType {
    override fun hashCode(): Int { counter.incrementAndGet(); return tag.hashCode() }
    override fun equals(other: Any?): Boolean { counter.incrementAndGet(); return other is CountingLeaf && other.tag == tag }
    override fun resolveMember(name: String, location: PyExpression?, direction: AccessDirection, resolveContext: PyResolveContext): List<RatedResolveResult>? = null
    override fun getCompletionVariants(completionPrefix: String?, location: PsiElement, context: ProcessingContext): Array<out Any> = emptyArray()
    override val name: String get() = tag
    override val isBuiltin: Boolean get() = false
    override fun assertValid(message: String?) {}
  }

  /** Non-memoizing wrapper delegating into [child]; distinct [tag]s over a shared child give the DAG its branching. */
  private inner class Wrapper(private val tag: Int, private val child: PyType) : PyType {
    override fun hashCode(): Int { counter.incrementAndGet(); return 31 * tag + child.hashCode() }
    override fun equals(other: Any?): Boolean { counter.incrementAndGet(); return other is Wrapper && other.tag == tag && other.child == child }
    override fun resolveMember(name: String, location: PyExpression?, direction: AccessDirection, resolveContext: PyResolveContext): List<RatedResolveResult>? = null
    override fun getCompletionVariants(completionPrefix: String?, location: PsiElement, context: ProcessingContext): Array<out Any> = emptyArray()
    override val name: String get() = "W$tag"
    override val isBuiltin: Boolean get() = false
    override fun assertValid(message: String?) {}
  }

  /** Leaf with a fixed hash but identity-by-[tag] equals, to force a hash collision between distinct members. */
  private class CollidingLeaf(private val tag: String) : PyType {
    override fun hashCode(): Int = 0
    override fun equals(other: Any?): Boolean = other is CollidingLeaf && other.tag == tag
    override fun resolveMember(name: String, location: PyExpression?, direction: AccessDirection, resolveContext: PyResolveContext): List<RatedResolveResult>? = null
    override fun getCompletionVariants(completionPrefix: String?, location: PsiElement, context: ProcessingContext): Array<out Any> = emptyArray()
    override val name: String get() = tag
    override val isBuiltin: Boolean get() = false
    override fun assertValid(message: String?) {}
  }

  private fun buildSharedDag(depth: Int, leafA: PyType, leafB: PyType, make: (List<PyType?>) -> PyType): PyType {
    var node: PyType = make(listOf(leafA, leafB))
    repeat(depth) { node = make(listOf(Wrapper(0, node), Wrapper(1, node))) }
    return node
  }

  @Test
  fun `building a shared-subtree composite DAG does not blow up hashCode`() {
    assertTimeoutPreemptively(Duration.ofSeconds(60)) {
      for ((name, make) in composites) {
        counter.set(0)
        buildSharedDag(DEPTH, CountingLeaf("A"), CountingLeaf("B"), make).hashCode()
        assertTrue(counter.get() < LINEAR_BOUND) { "$name: hashCode blew up: ${counter.get()} (want < $LINEAR_BOUND at depth $DEPTH)" }
      }
    }
  }

  @Test
  fun `equals of two distinct nested composites short-circuits on hashCode`() {
    assertTimeoutPreemptively(Duration.ofSeconds(60)) {
      for ((name, make) in composites) {
        val a = buildSharedDag(DEPTH, CountingLeaf("A"), CountingLeaf("B"), make)
        val b = buildSharedDag(DEPTH, CountingLeaf("A"), CountingLeaf("X"), make)
        counter.set(0)
        assertFalse(a == b) { "$name: DAGs differing at the deepest leaf must not be equal" }
        assertTrue(counter.get() < LINEAR_BOUND) { "$name: unequal-equals walked members: ${counter.get()} (want < $LINEAR_BOUND)" }
      }
    }
  }

  @Test
  fun `equal nested composites stay equal and hash-consistent`() {
    for ((name, make) in composites) {
      val a = buildSharedDag(DEPTH, CountingLeaf("A"), CountingLeaf("B"), make)
      val b = buildSharedDag(DEPTH, CountingLeaf("A"), CountingLeaf("B"), make)
      assertEquals(a, b) { "$name: structurally identical DAGs must be equal" }
      assertEquals(a.hashCode(), b.hashCode()) { "$name: equal composites must have equal hash codes" }
    }
  }

  @Test
  fun `composite equality is order-independent`() {
    for ((name, make) in composites) {
      val a = CountingLeaf("A")
      val b = CountingLeaf("B")
      assertEquals(make(listOf(a, b)), make(listOf(b, a))) { "$name: member order must not affect equality" }
    }
  }

  @Test
  fun `hash-colliding members do not produce false equality`() {
    for ((name, make) in composites) {
      val common = CountingLeaf("C")
      val x = make(listOf(CollidingLeaf("P"), common))
      val y = make(listOf(CollidingLeaf("Q"), common))
      assertEquals(x.hashCode(), y.hashCode()) { "$name: colliding leaves must give equal composite hash codes (precondition)" }
      assertNotEquals(x, y) { "$name: the hashCode fast path must fall through to the member-set comparison" }
    }
  }

  @Test
  fun `composites of different kinds are never equal`() {
    val a = CountingLeaf("A")
    val b = CountingLeaf("B")
    val union = PyUnionType.union(listOf(a, b))!!
    val intersection = PyIntersectionType.intersection(listOf(a, b))!!
    // Same member set -> identical hashCode, so this also checks the exact-class gate precedes the fast path.
    assertEquals(union.hashCode(), intersection.hashCode())
    assertNotEquals(union, intersection)
    assertNotEquals(intersection, union)
  }

  private companion object {
    const val DEPTH = 22
    const val LINEAR_BOUND = 100_000L
  }
}
