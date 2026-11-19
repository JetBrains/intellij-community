// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.filtering

import com.intellij.debugger.DebuggerTestFixture
import com.intellij.debugger.DebuggerTestOptions
import com.intellij.debugger.debuggerTestFixture
import com.intellij.debugger.engine.DebuggerManagerThreadImpl
import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.debugger.impl.OutputChecker
import com.intellij.debugger.streams.core.lib.LibrarySupportProvider
import com.intellij.debugger.streams.core.psi.impl.DebuggerPositionResolverImpl
import com.intellij.debugger.streams.core.wrapper.StreamChain
import com.intellij.debugger.streams.lib.impl.StandardLibrarySupportProvider
import com.intellij.debugger.streams.test.StreamDebuggerTestUtil
import com.intellij.execution.ExecutionTestOptions
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.util.ThrowableRunnable
import com.intellij.xdebugger.XSourcePosition
import com.sun.jdi.Method
import kotlin.time.Duration.Companion.seconds

/**
 * Creates the fixture of the tests of the stream-chain traceability filtering algorithm.
 *
 * The fixture launches a real debuggee under JDI, stops to obtain the live [com.sun.jdi.Method] and then,
 * for each [BytecodePosition], simulates a debugger position at a chosen bytecode offset and runs filtering.
 * The main module has the language level 16.
 * Declare it as an instance field of a `@TestApplication` class.
 */
internal fun streamChainFilteringFixture(): TestFixture<StreamChainFilteringFixture> = testFixture("streamChainFilteringFixture") {
  val execution = ExecutionTestOptions().withSetUpModule { module ->
    IdeaTestUtil.setModuleLanguageLevel(module, LanguageLevel.JDK_16)
  }
  val debugger = debuggerTestFixture(
    StreamDebuggerTestUtil.getTestDataPath("filtering"),
    { appPath, outputPath -> OutputChecker({ appPath }, { outputPath }) },
    DebuggerTestOptions().withExecution(execution),
  ).init()
  initialized(StreamChainFilteringFixture(debugger)) {}
}

/** The state and the helpers of a test that [streamChainFilteringFixture] creates. */
internal class StreamChainFilteringFixture(val debugger: DebuggerTestFixture) {
  private val positionResolver = DebuggerPositionResolverImpl()

  private fun getLibrarySupportProvider(): LibrarySupportProvider = StandardLibrarySupportProvider()

  private suspend fun findChains(position: XSourcePosition): List<StreamChain> = readAction {
    val element = positionResolver.getNearestElementToBreakpoint(debugger.project, position) ?: return@readAction emptyList()
    val builder = getLibrarySupportProvider().getChainBuilder()
    if (builder.isChainExists(element)) builder.build(element) else emptyList()
  }

  private suspend fun filterTraceable(
    chains: List<StreamChain>,
    position: XSourcePosition,
    method: Method,
    bytecodeOffset: Long,
  ): List<StreamChain> {
    val contextElement = readAction { positionResolver.getNearestElementToBreakpoint(debugger.project, position) } ?: return chains
    return filterTraceableStreams(chains, position, contextElement, method, bytecodeOffset)
  }

  /**
   * Launches [className] from `testData/filtering/src`, stops at its single `// Breakpoint!` and prints the golden
   * report for each of [positions]. Offsets are resolved from the stopped method's bytecode (simulated positions),
   * so all positions must lie inside that method - see [doFilteringTestAtBreakpoint] for a position in a different
   * method (e.g. a lambda body).
   */
  fun doFilteringTest(className: String, vararg positions: BytecodePosition): Unit = debugger.runTest(ThrowableRunnable {
    debugger.createLocalProcess(className)
    debugger.onBreakpoint { suspendContext ->
      DebuggerManagerThreadImpl.assertIsManagerThread()

      val method = suspendContext.frameProxy!!.location().method()
      val invokes = extractInvokesFromBytecode(method)
      val resolved = positions.map { anchor ->
        val offset = resolveSymbolicPositions(anchor, invokes)
        ResolvedPosition(anchor.toString(), method, offset, sourcePositionAt(method, offset))
      }
      timeoutRunBlocking(30.seconds) {
        resolved.forEach { reportPosition(it) }
      }
      debugger.resume(suspendContext)
    }
  })

  /**
   * Launches [className], stops at its single `// Breakpoint!` and prints the golden report at the *actual* stop
   * position (the top frame's real [com.sun.jdi.Location]). Use when the interesting position is not in the
   * launched `main` method - ex. inside a lambda body, which compiles to a separate synthetic method.
   */
  fun doFilteringTestAtBreakpoint(className: String): Unit = debugger.runTest(ThrowableRunnable {
    debugger.createLocalProcess(className)
    debugger.onBreakpoint { suspendContext ->
      val location = suspendContext.frameProxy!!.location()
      val position = DebuggerUtilsEx.toXSourcePosition(
        runReadActionBlocking { debugger.debugProcess!!.positionManager.getSourcePosition(location) }
      ) ?: error("No XSourcePosition at breakpoint")
      val resolved = ResolvedPosition("breakpoint", location.method(), location.codeIndex(), position)
      timeoutRunBlocking(30.seconds) {
        reportPosition(resolved)
      }
      debugger.resume(suspendContext)
    }
  })

  private fun sourcePositionAt(method: Method, offset: Long): XSourcePosition {
    val location = method.locationOfCodeIndex(offset) ?: error("No location for bytecode offset $offset")
    val sourcePosition = runReadActionBlocking {
      debugger.debugProcess!!.positionManager.getSourcePosition(location)
    }
    return DebuggerUtilsEx.toXSourcePosition(sourcePosition) ?: error("No XSourcePosition for bytecode offset $offset")
  }

  private suspend fun reportPosition(position: ResolvedPosition) {
    val chains = findChains(position.position)
    val traceable = filterTraceable(chains, position.position, position.method, position.offset)
    val ordered = chains.sortedBy { it.terminationCall.textRange.endOffset }
    val traceableKeys = traceable.mapTo(HashSet()) { it.terminationCall.textRange.endOffset }
    val report = buildString {
      appendLine("=== ${position.label} (bytecode offset ${position.offset}) ===")
      if (ordered.isEmpty()) {
        appendLine("  <no streams detected>")
      }
      else {
        ordered.forEachIndexed { index, chain ->
          val status = if (chain.terminationCall.textRange.endOffset in traceableKeys) "traceable" else "filtered "
          appendLine("  [$index] $status ${chain.compactText}")
        }
      }
    }
    debugger.println(report.trimEnd(), ProcessOutputTypes.SYSTEM)
  }

  private class ResolvedPosition(val label: String, val method: Method, val offset: Long, val position: XSourcePosition)
}
