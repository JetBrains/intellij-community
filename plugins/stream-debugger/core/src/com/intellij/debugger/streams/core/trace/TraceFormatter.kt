// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.core.trace

import org.jetbrains.annotations.ApiStatus

/**
 * Renders a stream trace into a human-readable string
 */
@ApiStatus.Internal
fun formatTrace(trace: List<TraceInfo>): String = buildString {
  for (info in trace) {
    appendLine(info.call.name + info.call.genericArguments)
    appendLine("    before: " + traceToString(info.valuesOrderBefore.values))
    appendLine("    after: " + traceToString(info.valuesOrderAfter.values))
  }
}

@ApiStatus.Internal
fun formatResolvedTrace(result: ResolvedTracingResult): String = buildString {
  val resolvedChain = result.resolvedChain
  for (call in resolvedChain.intermediateCalls) {
    append(formatBeforeAndAfter(call.stateBefore, call.stateAfter))
  }
  val terminator = resolvedChain.terminator
  append(formatBeforeAndAfter(terminator.stateBefore, terminator.stateAfter))
}

private fun formatBeforeAndAfter(before: NextAwareState?, after: PrevAwareState?): String = buildString {
  val call = before?.nextCall ?: after?.prevCall
  appendLine("mappings for " + (call?.name ?: "<unknown>"))
  appendLine("  direct:")
  if (before != null) append(formatMapping(before.trace, forward = true) { before.getNextValues(it) })
  else appendLine("    no")
  appendLine("  reverse:")
  if (after != null) append(formatMapping(after.trace, forward = false) { after.getPrevValues(it) })
  else appendLine("    not found")
}

private fun formatMapping(
  values: List<TraceElement>,
  forward: Boolean,
  mapper: (TraceElement) -> List<TraceElement>,
): String = buildString {
  if (values.isEmpty()) appendLine("    empty")
  for (element in values) {
    val mapped = traceToString(mapper(element))
    appendLine(if (forward) "    ${element.time} -> $mapped" else "    $mapped <- ${element.time}")
  }
}

private fun traceToString(trace: Collection<TraceElement>): String =
  trace.map { it.time }.sorted().joinToString(",").ifEmpty { "nothing" }
