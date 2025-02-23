package com.intellij.debugger.streams.core.testFramework

import com.intellij.debugger.streams.core.lib.LibrarySupportProvider
import com.intellij.debugger.streams.core.lib.ResolverFactory
import com.intellij.debugger.streams.core.psi.DebuggerPositionResolver
import com.intellij.debugger.streams.core.resolve.ResolvedStreamCall
import com.intellij.debugger.streams.core.resolve.ResolvedStreamChain
import com.intellij.debugger.streams.core.trace.*
import com.intellij.debugger.streams.core.trace.impl.TraceResultInterpreterImpl
import com.intellij.debugger.streams.core.wrapper.StreamChain
import com.intellij.debugger.streams.core.wrapper.StreamChainBuilder
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Key
import com.intellij.xdebugger.XDebugSession
import one.util.streamex.StreamEx
import java.util.function.Function
import kotlin.test.*

abstract class TraceExecutionTestHelper(
  private val session: XDebugSession,
  private val librarySupportProvider: LibrarySupportProvider,
  private val debuggerPositionResolver: DebuggerPositionResolver,
  private val LOG: Logger,
) {
  private val project = session.project

  enum class FailureReason {
    COMPILATION, EVALUATION, CHAIN_CONSTRUCTION
  }

  private fun createResultInterpreter(): TraceResultInterpreter {
    return TraceResultInterpreterImpl(librarySupportProvider.librarySupport.interpreterFactory)
  }

  private fun createXValueInterpreter(): XValueInterpreter {
    return librarySupportProvider.getXValueInterpreter(project)
  }

  private fun createResolverFactory(): ResolverFactory {
    return librarySupportProvider.librarySupport.resolverFactory
  }

  private fun createChainBuilder(): StreamChainBuilder {
    return librarySupportProvider.chainBuilder
  }

  protected open fun createExpressionBuilder(): TraceExpressionBuilder {
    return librarySupportProvider.getExpressionBuilder(project)
  }

  suspend fun onPause(chainSelector: ChainSelector, resultMustBeNull: Boolean? = null) {
    val chain = readAction {
      val elementAtBreakpoint = debuggerPositionResolver.getNearestElementToBreakpoint(session)
      val chains = if (elementAtBreakpoint == null) null else createChainBuilder().build(elementAtBreakpoint)
      if (chains.isNullOrEmpty()) null else chainSelector.select(chains)
    }

    if (chain == null) {
      complete(null, null, resultMustBeNull, null, FailureReason.CHAIN_CONSTRUCTION)
      return
    }

    val tracer = EvaluateExpressionTracer(session, createExpressionBuilder(), createResultInterpreter(), createXValueInterpreter())
    val trace = tracer.trace(chain)
    when (trace) {
      is StreamTracer.Result.Evaluated -> complete(chain, trace.result, resultMustBeNull, null, null)
      is StreamTracer.Result.EvaluationFailed -> complete(chain, null, resultMustBeNull, trace.message, FailureReason.EVALUATION)
      is StreamTracer.Result.CompilationFailed -> {
        LOG.warn("[" + getTestName() + "] Compilation failed.")
        complete(chain, null, resultMustBeNull, trace.message, FailureReason.COMPILATION)
      }
      StreamTracer.Result.Unknown -> TODO()
    }
  }

  private fun complete(
    chain: StreamChain?,
    result: TracingResult?,
    resultMustBeNull: Boolean?,
    error: String?,
    errorReason: FailureReason?,
  ) {
    try {
      if (error != null) {
        assertNotNull(errorReason)
        assertNotNull(chain)
        handleError(chain!!, error, errorReason!!)
      }
      else {
        assertNull(errorReason)
        assertNotNull(chain)

        handleSuccess(chain!!, result!!, resultMustBeNull)
      }
    }
    catch (t: Throwable) {
      val s = "Exception caught: " + t + ", " + t.message
      println(s, ProcessOutputTypes.SYSTEM)
      t.printStackTrace()
    }
    finally {
      resume()
    }
  }

  protected abstract fun resume()

  protected abstract fun getTestName(): String

  protected open fun handleResultValue(result: Value?, mustBeNull: Boolean) {
    if (mustBeNull) {
      assertNull(result)
    }
    else {
      assertNotNull(result)
    }
  }

  protected abstract fun println(s: String, processOutputType: Key<*>)

  protected abstract fun print(s: String, processOutputType: Key<*>)

  protected open fun handleError(chain: StreamChain, error: String, reason: FailureReason) {
    fail(error)
  }

  protected open fun handleSuccess(
    chain: StreamChain,
    result: TracingResult,
    resultMustBeNull: Boolean?,
  ) {
    assertNotNull(chain)
    assertNotNull(result)

    println(chain.text, ProcessOutputTypes.SYSTEM)

    val resultValue = result.result
    if (resultMustBeNull != null) {
      handleResultValue(resultValue.value, resultMustBeNull)
    }

    val trace = result.trace
    handleTrace(trace)

    val resolvedTrace = result.resolve(createResolverFactory())
    handleResolvedTrace(resolvedTrace)
  }

  private fun handleTrace(trace: List<TraceInfo>) {
    for (info in trace) {
      val name = info.call.name + info.call.genericArguments
      println(name, ProcessOutputTypes.SYSTEM)

      print("    before: ", ProcessOutputTypes.SYSTEM)
      val before = info.valuesOrderBefore
      println(traceToString(before.values), ProcessOutputTypes.SYSTEM)

      print("    after: ", ProcessOutputTypes.SYSTEM)
      val after = info.valuesOrderAfter
      println(traceToString(after.values), ProcessOutputTypes.SYSTEM)
    }
  }

  private fun handleResolvedTrace(result: ResolvedTracingResult) {
    val resolvedChain = result.resolvedChain

    checkChain(resolvedChain)
    checkTracesIsCorrectInBothDirections(resolvedChain)

    val terminator = resolvedChain.terminator
    resolvedChain.intermediateCalls.forEach { x: ResolvedStreamCall.Intermediate -> printBeforeAndAfterValues(x.stateBefore, x.stateAfter) }
    printBeforeAndAfterValues(terminator.stateBefore, terminator.stateAfter)
  }

  private fun printBeforeAndAfterValues(before: NextAwareState?, after: PrevAwareState?) {
    assertFalse(before == null && after == null)
    val call = before?.nextCall ?: after!!.prevCall
    assertNotNull(call)
    println("mappings for " + call!!.name, ProcessOutputTypes.SYSTEM)
    println("  direct:", ProcessOutputTypes.SYSTEM)
    if (before != null) {
      printMapping(before.trace, { value: TraceElement? -> before.getNextValues(value!!) }, Direction.FORWARD)
    }
    else {
      println("    no", ProcessOutputTypes.SYSTEM)
    }

    println("  reverse:", ProcessOutputTypes.SYSTEM)
    if (after != null) {
      printMapping(after.trace, { value: TraceElement? -> after.getPrevValues(value!!) }, Direction.BACKWARD)
    }
    else {
      println("    not found", ProcessOutputTypes.SYSTEM)
    }
  }

  private fun printMapping(
    values: List<TraceElement>,
    mapper: Function<in TraceElement, out List<TraceElement>>,
    direction: Direction,
  ) {
    if (values.isEmpty()) {
      println("    empty", ProcessOutputTypes.SYSTEM)
    }
    for (element in values) {
      val mappedValues = mapper.apply(element)
      val mapped = traceToString(mappedValues)
      val line = if (Direction.FORWARD == direction) element.time.toString() + " -> " + mapped else mapped + " <- " + element.time
      println("    $line", ProcessOutputTypes.SYSTEM)
    }
  }

  private enum class Direction {
    FORWARD, BACKWARD
  }

  private fun checkChain(chain: ResolvedStreamChain) {
    val intermediates = chain.intermediateCalls
    val terminator = chain.terminator
    if (intermediates.isEmpty()) {
      assertFalse(terminator.stateBefore is PrevAwareState)
    }

    checkIntermediates(chain.intermediateCalls)

    assertEquals(terminator.call.name, terminator.stateBefore.nextCall.name)
    val after = terminator.stateAfter
    if (after != null) {
      val terminatorCall = after.prevCall
      assertNotNull(terminatorCall)
      assertEquals(terminator.call.name, terminatorCall!!.name)
    }

    if (!intermediates.isEmpty()) {
      val lastIntermediate = intermediates[intermediates.size - 1]
      val stateAfterIntermediates = lastIntermediate.stateAfter
      assertIs<NextAwareState>(stateAfterIntermediates)
      assertEquals(terminator.call.name, (stateAfterIntermediates as NextAwareState).nextCall.name)
    }
  }

  private fun checkIntermediates(intermediates: List<ResolvedStreamCall.Intermediate>) {
    for (i in 0 until intermediates.size - 1) {
      val prev = intermediates[i]
      val next = intermediates[i + 1]
      assertSame<Any?>(prev.stateAfter, next.stateBefore)
      val prevCall = prev.stateAfter.prevCall
      assertNotNull(prevCall)
      assertEquals(prev.call.name, prevCall!!.name)
      assertEquals(next.call.name, next.stateBefore.nextCall.name)
    }
  }

  private fun checkTracesIsCorrectInBothDirections(resolvedChain: ResolvedStreamChain) {
    for (intermediate in resolvedChain.intermediateCalls) {
      checkNeighborTraces(intermediate.stateBefore, intermediate.stateAfter)
    }

    val terminator = resolvedChain.terminator
    val after = terminator.stateAfter
    if (after != null) {
      checkNeighborTraces(terminator.stateBefore, after)
    }
  }

  private fun checkNeighborTraces(left: NextAwareState, right: PrevAwareState) {
    val leftValues: Set<TraceElement> = HashSet(left.trace)
    val rightValues: Set<TraceElement> = HashSet(right.trace)

    checkThatMappingsIsCorrect(leftValues, rightValues, { value: TraceElement? -> left.getNextValues(value!!) }, { value: TraceElement? -> right.getPrevValues(value!!) })
    checkThatMappingsIsCorrect(rightValues, leftValues, { value: TraceElement? -> right.getPrevValues(value!!) }, { value: TraceElement? -> left.getNextValues(value!!) })
  }

  private fun checkThatMappingsIsCorrect(
    prev: Set<TraceElement>,
    next: Set<TraceElement>,
    toNext: Function<in TraceElement, out List<TraceElement>>,
    toPrev: Function<in TraceElement, out List<TraceElement>>,
  ) {
    for (leftElement in prev) {
      val mapToRight = toNext.apply(leftElement)
      for (rightElement in mapToRight) {
        assertTrue(next.contains(rightElement))
        assertTrue(toPrev.apply(rightElement).contains(leftElement))
      }
    }
  }

  private fun traceToString(trace: Collection<TraceElement>): String {
    return replaceIfEmpty(StreamEx.of(trace).map { obj: TraceElement -> obj.time }.sorted().joining(","))
  }

  private fun replaceIfEmpty(str: String): String {
    return str.ifEmpty { "nothing" }
  }
}