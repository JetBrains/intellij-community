// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.breakpoint

import com.intellij.debugger.streams.test.streamChainBuilderFixture
import com.intellij.debugger.streams.trace.breakpoint.BreakpointResolveResult
import com.intellij.debugger.streams.trace.breakpoint.JavaBreakpointPositionResolver
import com.intellij.debugger.streams.trace.breakpoint.JvmMethodSignature
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private const val STREAM = "java.util.stream.Stream"
private const val INT_STREAM = "java.util.stream.IntStream"

// Common Stream method signatures
private val STREAM_OF = JvmMethodSignature(STREAM, "of", listOf("java.lang.Object"), STREAM)
private val STREAM_OF_VARARG = JvmMethodSignature(STREAM, "of", listOf("java.lang.Object[]"), STREAM)
private val STREAM_MAP = JvmMethodSignature(STREAM, "map", listOf("java.util.function.Function"), STREAM)
private val STREAM_FILTER = JvmMethodSignature(STREAM, "filter", listOf("java.util.function.Predicate"), STREAM)
private val STREAM_SORTED = JvmMethodSignature(STREAM, "sorted", emptyList(), STREAM)
private val STREAM_DISTINCT = JvmMethodSignature(STREAM, "distinct", emptyList(), STREAM)
private val STREAM_TO_LIST = JvmMethodSignature(STREAM, "toList", emptyList(), "java.util.List")
private val STREAM_COLLECT = JvmMethodSignature(STREAM, "collect", listOf("java.util.stream.Collector"), "java.lang.Object")
private val STREAM_COUNT = JvmMethodSignature(STREAM, "count", emptyList(), "long")
private val COLLECTION_STREAM = JvmMethodSignature("java.util.Collection", "stream", emptyList(), STREAM)

// IntStream method signatures
private val INT_STREAM_OF = JvmMethodSignature(INT_STREAM, "of", listOf("int[]"), INT_STREAM)
private val INT_STREAM_MAP = JvmMethodSignature(INT_STREAM, "map", listOf("java.util.function.IntUnaryOperator"), INT_STREAM)
private val INT_STREAM_SUM = JvmMethodSignature(INT_STREAM, "sum", emptyList(), "int")

@TestApplication
internal class JavaBreakpointPositionResolverTest {
  // some new operators were introduced after java 1.8
  private val builderFixture by streamChainBuilderFixture("breakpoint/resolving/java", LanguageLevel.JDK_21, LanguageLevel.JDK_16)

  /**
   * Like [doTest] but also asserts the [BreakpointResolveResult.Found.skipCount].
   * Use for chains whose qualifier method appears exactly once in the containing method,
   * or when the chain is the N-th occurrence and a specific skipCount is expected.
   */
  private fun doTestSkipCount(expectedSkipCount: Int, vararg expectedMethods: JvmMethodSignature) {
    val chains = builderFixture.buildChains()
    assertEquals(1, chains.size, "Expected exactly one stream chain")

    val resolver = JavaBreakpointPositionResolver()
    val result = timeoutRunBlocking { resolver.findBreakpointPositions(chains.single()) }

    assertTrue(result is BreakpointResolveResult.Found, "Expected BreakpointResolveResult.Found but got $result")
    val found = result as BreakpointResolveResult.Found

    assertEquals(expectedSkipCount, found.skipCount, "skipCount mismatch")

    val allMethods = buildList {
      found.qualifierExpressionMethod?.let { add(it) }
      addAll(found.intermediateStepsMethods)
      add(found.terminationOperationMethod)
    }
    assertEquals(expectedMethods.toList(), allMethods, "Method signatures mismatch")
  }

  /**
   * Resolves all chains in the fixture, sorts them by termination-call end offset (= source order),
   * and asserts the [BreakpointResolveResult.Found.skipCount] and method signatures for each one.
   *
   * Sorting by termination end offset gives a stable, source-code ordering even though the
   * chain builder collects termination calls in a HashSet.
   *
   * @param expected pairs of (expectedSkipCount, expectedMethodSignatures) in source order
   */
  private fun doTestLinkedChains(vararg expected: Pair<Int, Array<out JvmMethodSignature>>) {
    val chains = builderFixture.buildChains()
    assertEquals(expected.size, chains.size, "Expected ${expected.size} stream chains")

    val sortedChains = chains.sortedBy { it.terminationCall.textRange.endOffset }

    val resolver = JavaBreakpointPositionResolver()
    sortedChains.forEachIndexed { index, chain ->
      val result = timeoutRunBlocking { resolver.findBreakpointPositions(chain) }
      assertTrue(result is BreakpointResolveResult.Found, "Chain #$index: Expected Found but got $result")
      val found = result as BreakpointResolveResult.Found

      val (expectedSkipCount, expectedMethods) = expected[index]
      assertEquals(expectedSkipCount, found.skipCount, "Chain #$index: skipCount mismatch")

      val allMethods = buildList {
        found.qualifierExpressionMethod?.let { add(it) }
        addAll(found.intermediateStepsMethods)
        add(found.terminationOperationMethod)
      }
      assertEquals(expectedMethods.toList(), allMethods, "Chain #$index: method signatures mismatch")
    }
  }

  private fun doTest(vararg expectedMethods: JvmMethodSignature) {
    val chains = builderFixture.buildChains()
    assertEquals(1, chains.size, "Expected exactly one stream chain")

    val resolver = JavaBreakpointPositionResolver()
    val result = timeoutRunBlocking {
      resolver.findBreakpointPositions(chains.single())
    }

    // Verify result is Found
    assertTrue(result is BreakpointResolveResult.Found, "Expected BreakpointResolveResult.Found but got $result")
    val found = result as BreakpointResolveResult.Found
    assertEquals(0, found.skipCount, "skipCount must be 0 for a simple chain")

    // Collect all method signatures in order: qualifier + intermediate + termination
    val allMethods = buildList {
      found.qualifierExpressionMethod?.let { add(it) }
      addAll(found.intermediateStepsMethods)
      add(found.terminationOperationMethod)
    }

    // Verify signatures match exactly (including order)
    assertEquals(
      expectedMethods.size,
      allMethods.size,
      "Expected ${expectedMethods.size} operations but got ${allMethods.size}"
    )

    assertEquals(
      expectedMethods.toList(),
      allMethods,
      "Method signatures mismatch"
    )
  }

  private fun doTestMultipleChains(vararg expectedResults: Array<out JvmMethodSignature>?) {
    val chains = builderFixture.buildChains()
    assertEquals(expectedResults.size, chains.size, "Expected ${expectedResults.size} stream chains")

    val resolver = JavaBreakpointPositionResolver()
    
    chains.forEachIndexed { index, chain ->
      val expected = expectedResults[index]
      val result = timeoutRunBlocking {
        resolver.findBreakpointPositions(chain)
      }

      @Suppress("KotlinConstantConditions")
      if (expected == null) {
        assertTrue(result is BreakpointResolveResult.NotFound, "Chain #$index: Expected NotFound but got $result")
      } else {
        assertTrue(result is BreakpointResolveResult.Found, "Chain #$index: Expected Found but got $result")
        val found = result as BreakpointResolveResult.Found
        assertEquals(0, found.skipCount, "Chain #$index: skipCount must be 0")

        val allMethods = buildList {
          found.qualifierExpressionMethod?.let { add(it) }
          addAll(found.intermediateStepsMethods)
          add(found.terminationOperationMethod)
        }

        assertEquals(
          expected.toList(),
          allMethods,
          "Chain #$index: Method signatures mismatch"
        )
      }
    }
  }

  //region Positive cases

  @Test
  fun testSimpleChain() {
    doTest(STREAM_OF_VARARG, STREAM_MAP, STREAM_FILTER, STREAM_TO_LIST)
  }

  @Test
  fun testSingleElementOf() {
    doTest(STREAM_OF, STREAM_MAP, STREAM_TO_LIST)
  }

  @Test
  fun testVarargsOf() {
    doTest(STREAM_OF_VARARG, STREAM_MAP, STREAM_TO_LIST)
  }

  @Test
  fun testQualifierStaticImport() {
    doTest(STREAM_OF_VARARG, STREAM_MAP, STREAM_TO_LIST)
  }

  @Test
  fun testWithWhitespace() {
    doTest(STREAM_OF_VARARG, STREAM_MAP, STREAM_TO_LIST)
  }

  @Test
  fun testWeirdFormatting() {
    doTest(STREAM_OF_VARARG, STREAM_MAP, STREAM_TO_LIST)
  }

  @Test
  fun testSpacesAroundDots() {
    doTest(STREAM_OF_VARARG, STREAM_MAP, STREAM_TO_LIST)
  }

  @Test
  fun testPrimitiveStream() {
    doTest(INT_STREAM_OF, INT_STREAM_MAP, INT_STREAM_SUM)
  }

  @Test
  fun testNoIntermediate() {
    doTest(STREAM_OF_VARARG, STREAM_TO_LIST)
  }

  //endregion

  //region Negative cases

  @Test
  fun testNestedStreams() {
    // When builder returns multiple chains (outer and nested):
    // - Outer chain should return Found
    // - Nested chain should return NotFound
    val chains = builderFixture.buildChains()
    if (chains.size == 1) {
      doTest(STREAM_OF_VARARG, STREAM_MAP, STREAM_TO_LIST)
    } else {
      // If both chains are found, outer should succeed, nested should return NotFound
      doTestMultipleChains(
        arrayOf(STREAM_OF_VARARG, STREAM_MAP, STREAM_TO_LIST),  // outer chain: Found
        null  // nested chain: NotFound
      )
    }
  }

  /*@Ignore("nested streams are not supported yet")
  @Test
  fun testNestedStreamCaretOnInner() {
    // Caret is on the nested stream, so builder should find both chains
    // - Nested chain (where caret is): NotFound
    // - Outer chain: Found
    doTestMultipleChains(
      null,
      arrayOf(STREAM_OF_VARARG, STREAM_MAP, STREAM_TO_LIST)
    )
  }*/

  @Test
  fun testStreamInsideBinaryExpression() {
    doTest(STREAM_OF_VARARG, STREAM_COUNT)
  }

  @Test
  fun testAmbiguousPosition() {
    doTestMultipleChains(
      arrayOf(STREAM_OF_VARARG, STREAM_COUNT),
      arrayOf(STREAM_OF_VARARG, STREAM_COUNT)
    )
  }

  @Test
  fun testComplexLambda() {
    doTest(STREAM_OF_VARARG, STREAM_MAP, STREAM_TO_LIST)
  }

  @Test
  fun testMethodReference() {
    doTest(STREAM_OF_VARARG, STREAM_MAP, STREAM_TO_LIST)
  }

  @Test
  fun testMultipleMapOperations() {
    doTest(STREAM_OF_VARARG, STREAM_MAP, STREAM_MAP, STREAM_MAP, STREAM_TO_LIST)
  }

  //endregion

  //region Skip count tests

  /**
   * When the qualifier method (`Stream.of`) appears only once in the containing method,
   * no JDI events need to be skipped.
   */
  @Test
  fun testSkipCountZeroUniqueQualifier() {
    doTestSkipCount(0, STREAM_OF_VARARG, STREAM_MAP, STREAM_TO_LIST)
  }

  /**
   * Four linked chains that all share `Collection.stream()` as their qualifier method.
   *
   * Expected skip counts (sorted by termination-call end offset = source order):
   * - Chain 1 (`Stream.of().map().collect()`):           qualifier = `Stream.of`, unique → skipCount = 0
   * - Chain 2 (`.stream().filter().collect()`):          1st `stream()` call in the method → skipCount = 0
   * - Chain 3 (`.stream().sorted().collect()`):          2nd `stream()` call → skipCount = 1
   * - Chain 4 (`.stream().distinct().collect()`):        3rd `stream()` call → skipCount = 2
   */
  @Test
  fun testSkipCountLinkedChains() {
    doTestLinkedChains(
      0 to arrayOf(STREAM_OF_VARARG, STREAM_MAP, STREAM_COLLECT),
      0 to arrayOf(COLLECTION_STREAM, STREAM_FILTER, STREAM_COLLECT),
      1 to arrayOf(COLLECTION_STREAM, STREAM_SORTED, STREAM_COLLECT),
      2 to arrayOf(COLLECTION_STREAM, STREAM_DISTINCT, STREAM_COLLECT),
    )
  }

  /**
   * Two independent `list.stream().count()` statements precede the traced chain.
   * They have already been executed before the VM stopped at the breakpoint, so their
   * `MethodExitRequest` events are long gone — the PSI visitor scopes to the current
   * statement only, and the third chain's skipCount must be 0.
   */
  @Test
  fun testSkipCountPriorStreams() {
    doTestSkipCount(0, COLLECTION_STREAM, STREAM_MAP, STREAM_TO_LIST)
  }

  //endregion
}
