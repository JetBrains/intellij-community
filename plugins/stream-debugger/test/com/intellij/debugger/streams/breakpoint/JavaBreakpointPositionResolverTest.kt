// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.breakpoint

import com.intellij.debugger.streams.test.StreamChainBuilderTestCase
import com.intellij.debugger.streams.trace.breakpoint.BreakpointResolveResult
import com.intellij.debugger.streams.trace.breakpoint.JavaBreakpointPositionResolver
import com.intellij.debugger.streams.trace.breakpoint.JvmMethodSignature
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import org.junit.Ignore
import java.io.File

private const val STREAM = "java.util.stream.Stream"
private const val INT_STREAM = "java.util.stream.IntStream"

// Common Stream method signatures
private val STREAM_OF = JvmMethodSignature(STREAM, "of", listOf("java.lang.Object"), STREAM)
private val STREAM_OF_VARARG = JvmMethodSignature(STREAM, "of", listOf("java.lang.Object[]"), STREAM)
private val STREAM_MAP = JvmMethodSignature(STREAM, "map", listOf("java.util.function.Function"), STREAM)
private val STREAM_FILTER = JvmMethodSignature(STREAM, "filter", listOf("java.util.function.Predicate"), STREAM)
private val STREAM_TO_LIST = JvmMethodSignature(STREAM, "toList", emptyList(), "java.util.List")
private val STREAM_COUNT = JvmMethodSignature(STREAM, "count", emptyList(), "long")

// IntStream method signatures
private val INT_STREAM_OF = JvmMethodSignature(INT_STREAM, "of", listOf("int[]"), INT_STREAM)
private val INT_STREAM_MAP = JvmMethodSignature(INT_STREAM, "map", listOf("java.util.function.IntUnaryOperator"), INT_STREAM)
private val INT_STREAM_SUM = JvmMethodSignature(INT_STREAM, "sum", emptyList(), "int")

internal class JavaBreakpointPositionResolverTest : StreamChainBuilderTestCase() {
  override fun getRelativeTestPath(): String =
    "breakpoint${File.separator}resolving${File.separator}java"

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()
    languageLevel = LanguageLevel.JDK_16
  }

  // some new operators were introduced after java 1.8
  override fun getProjectJDK(): Sdk = IdeaTestUtil.getMockJdk21()

  private fun doTest(vararg expectedMethods: JvmMethodSignature) {
    val chains = buildChains()
    assertEquals("Expected exactly one stream chain", 1, chains.size)

    val resolver = JavaBreakpointPositionResolver()
    val result = timeoutRunBlocking {
      resolver.findBreakpointPositions(chains.single())
    }

    // Verify result is Found
    assertTrue("Expected BreakpointResolveResult.Found but got $result", result is BreakpointResolveResult.Found)
    val found = result as BreakpointResolveResult.Found

    // Collect all method signatures in order: qualifier + intermediate + termination
    val allMethods = buildList {
      found.qualifierExpressionMethod?.let { add(it) }
      addAll(found.intermediateStepsMethods)
      add(found.terminationOperationMethod)
    }

    // Verify signatures match exactly (including order)
    assertEquals(
      "Expected ${expectedMethods.size} operations but got ${allMethods.size}",
      expectedMethods.size,
      allMethods.size
    )

    assertEquals(
      "Method signatures mismatch",
      expectedMethods.toList(),
      allMethods
    )
  }

  private fun doTestMultipleChains(vararg expectedResults: Array<out JvmMethodSignature>?) {
    val chains = buildChains()
    assertEquals("Expected ${expectedResults.size} stream chains", expectedResults.size, chains.size)

    val resolver = JavaBreakpointPositionResolver()
    
    chains.forEachIndexed { index, chain ->
      val expected = expectedResults[index]
      val result = timeoutRunBlocking {
        resolver.findBreakpointPositions(chain)
      }

      @Suppress("KotlinConstantConditions")
      if (expected == null) {
        assertTrue("Chain #$index: Expected NotFound but got $result", result is BreakpointResolveResult.NotFound)
      } else {
        assertTrue("Chain #$index: Expected Found but got $result", result is BreakpointResolveResult.Found)
        val found = result as BreakpointResolveResult.Found
        
        val allMethods = buildList {
          found.qualifierExpressionMethod?.let { add(it) }
          addAll(found.intermediateStepsMethods)
          add(found.terminationOperationMethod)
        }

        assertEquals(
          "Chain #$index: Method signatures mismatch",
          expected.toList(),
          allMethods
        )
      }
    }
  }

  //region Positive cases

  fun testSimpleChain() {
    doTest(STREAM_OF_VARARG, STREAM_MAP, STREAM_FILTER, STREAM_TO_LIST)
  }

  fun testSingleElementOf() {
    doTest(STREAM_OF, STREAM_MAP, STREAM_TO_LIST)
  }

  fun testVarargsOf() {
    doTest(STREAM_OF_VARARG, STREAM_MAP, STREAM_TO_LIST)
  }

  fun testQualifierStaticImport() {
    doTest(STREAM_OF_VARARG, STREAM_MAP, STREAM_TO_LIST)
  }

  fun testWithWhitespace() {
    doTest(STREAM_OF_VARARG, STREAM_MAP, STREAM_TO_LIST)
  }

  fun testWeirdFormatting() {
    doTest(STREAM_OF_VARARG, STREAM_MAP, STREAM_TO_LIST)
  }

  fun testSpacesAroundDots() {
    doTest(STREAM_OF_VARARG, STREAM_MAP, STREAM_TO_LIST)
  }

  fun testPrimitiveStream() {
    doTest(INT_STREAM_OF, INT_STREAM_MAP, INT_STREAM_SUM)
  }

  fun testNoIntermediate() {
    doTest(STREAM_OF_VARARG, STREAM_TO_LIST)
  }

  //endregion

  //region Negative cases

  fun testNestedStreams() {
    // When builder returns multiple chains (outer and nested):
    // - Outer chain should return Found
    // - Nested chain should return NotFound
    val chains = buildChains()
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

  @Ignore("nested streams are not supported yet")
  fun testNestedStreamCaretOnInner() {
    // Caret is on the nested stream, so builder should find both chains
    // - Nested chain (where caret is): NotFound
    // - Outer chain: Found
    doTestMultipleChains(
      null,
      arrayOf(STREAM_OF_VARARG, STREAM_MAP, STREAM_TO_LIST)
    )
  }

  fun testStreamInsideBinaryExpression() {
    doTest(STREAM_OF_VARARG, STREAM_COUNT)
  }

  fun testAmbiguousPosition() {
    doTestMultipleChains(
      arrayOf(STREAM_OF_VARARG, STREAM_COUNT),
      arrayOf(STREAM_OF_VARARG, STREAM_COUNT)
    )
  }

  fun testComplexLambda() {
    doTest(STREAM_OF_VARARG, STREAM_MAP, STREAM_TO_LIST)
  }

  fun testMethodReference() {
    doTest(STREAM_OF_VARARG, STREAM_MAP, STREAM_TO_LIST)
  }

  fun testMultipleMapOperations() {
    doTest(STREAM_OF_VARARG, STREAM_MAP, STREAM_MAP, STREAM_MAP, STREAM_TO_LIST)
  }

  //endregion
}
