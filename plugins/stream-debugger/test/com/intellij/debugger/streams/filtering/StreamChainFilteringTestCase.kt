// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.filtering

import com.intellij.debugger.DebuggerTestCase
import com.intellij.debugger.engine.DebuggerManagerThreadImpl
import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.debugger.impl.OutputChecker
import com.intellij.debugger.streams.core.lib.LibrarySupportProvider
import com.intellij.debugger.streams.core.psi.impl.DebuggerPositionResolverImpl
import com.intellij.debugger.streams.core.wrapper.StreamChain
import com.intellij.debugger.streams.lib.impl.StandardLibrarySupportProvider
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.application.PluginPathManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.diagnostic.Logger
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.xdebugger.XSourcePosition
import com.sun.jdi.Method
import kotlin.io.path.Path
import kotlin.time.Duration.Companion.seconds

/**
 * Base class for tests of the stream-chain traceability filtering algorithm.
 *
 * The harness launches a real debuggee under JDI, stops to obtain the live [com.sun.jdi.Method] and then,
 * for each [BytecodePosition], simulates a debugger position at a chosen bytecode offset and runs filtering.
 */
abstract class StreamChainFilteringTestCase : DebuggerTestCase() {
  @JvmField
  protected val LOG: Logger = Logger.getInstance(javaClass)

  private val positionResolver = DebuggerPositionResolverImpl()

  override fun initOutputChecker(): OutputChecker = OutputChecker({ testAppPath }, { appOutputPath })

  override fun setUpModule() {
    super.setUpModule()
    IdeaTestUtil.setModuleLanguageLevel(myModule, LanguageLevel.JDK_16)
  }

  override fun getTestAppPath(): String =
    Path(PluginPathManager.getPluginHomePath("stream-debugger")).resolve("testData/filtering/").toString()

  protected open fun getLibrarySupportProvider(): LibrarySupportProvider = StandardLibrarySupportProvider()

  protected open suspend fun findChains(position: XSourcePosition): List<StreamChain> = readAction {
    val element = positionResolver.getNearestElementToBreakpoint(project, position) ?: return@readAction emptyList()
    val builder = getLibrarySupportProvider().getChainBuilder()
    if (builder.isChainExists(element)) builder.build(element) else emptyList()
  }

  /**
   * Entry point of the traceable-chain filtering algorithm.
   *
   * TODO(IDEA-385556): replace this stub with the real implementation. For now it returns all detected chains
   * (no bytecode-based filtering), so every chain shows up as `traceable` in the goldens.
   */
  protected open suspend fun filterTraceable(
    chains: List<StreamChain>,
    position: XSourcePosition,
    method: Method,
    bytecodeOffset: Long,
  ): List<StreamChain> = chains

  /**
   * Launches [className] from `testData/filtering/src`, stops at its single `// Breakpoint!` and prints the golden
   * report for each of [positions]. Offsets are resolved from the stopped method's bytecode (simulated positions),
   * so all positions must lie inside that method - see [doFilteringTestAtBreakpoint] for a position in a different
   * method (e.g. a lambda body).
   */
  protected fun doFilteringTest(className: String, vararg positions: BytecodePosition) {
    createLocalProcess(className)
    onBreakpoint { suspendContext ->
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
      resume(suspendContext)
    }
  }

  /**
   * Launches [className], stops at its single `// Breakpoint!` and prints the golden report at the *actual* stop
   * position (the top frame's real [com.sun.jdi.Location]). Use when the interesting position is not in the
   * launched `main` method - ex. inside a lambda body, which compiles to a separate synthetic method.
   */
  protected fun doFilteringTestAtBreakpoint(className: String) {
    createLocalProcess(className)
    onBreakpoint { suspendContext ->
      val location = suspendContext.frameProxy!!.location()
      val position = DebuggerUtilsEx.toXSourcePosition(
        runReadActionBlocking { debugProcess.positionManager.getSourcePosition(location) }
      ) ?: error("No XSourcePosition at breakpoint")
      val resolved = ResolvedPosition("breakpoint", location.method(), location.codeIndex(), position)
      timeoutRunBlocking(30.seconds) {
        reportPosition(resolved)
      }
      resume(suspendContext)
    }
  }

  private fun sourcePositionAt(method: Method, offset: Long): XSourcePosition {
    val location = method.locationOfCodeIndex(offset) ?: error("No location for bytecode offset $offset")
    val sourcePosition = runReadActionBlocking {
      debugProcess.positionManager.getSourcePosition(location)
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
    println(report.trimEnd(), ProcessOutputTypes.SYSTEM)
  }

  private class ResolvedPosition(val label: String, val method: Method, val offset: Long, val position: XSourcePosition)
}
