package com.intellij.debugger.streams.core.trace.impl.interpret

import com.intellij.debugger.streams.core.trace.TraceElement
import com.intellij.debugger.streams.core.trace.TraceInfo
import com.intellij.debugger.streams.core.wrapper.StreamCall

class ValuesOrderInfo(
  private val streamCall: StreamCall,
  private val before: Map<Int, TraceElement>,
  private val after: Map<Int, TraceElement>,
  private val direct: Map<TraceElement, List<TraceElement>>? = null,
  private val reverse: Map<TraceElement, List<TraceElement>>? = null,
) : TraceInfo {
  constructor(call: StreamCall, before: Map<Int, TraceElement>, after: Map<Int, TraceElement>) : this(call, before, after, null, null)

  override fun getCall(): StreamCall = streamCall

  override fun getValuesOrderBefore(): Map<Int, TraceElement> = before

  override fun getValuesOrderAfter(): Map<Int, TraceElement> = after

  override fun getDirectTrace(): Map<TraceElement, List<TraceElement>>? = direct

  override fun getReverseTrace(): Map<TraceElement, List<TraceElement>>? = reverse

  companion object {
    fun empty(call: StreamCall): TraceInfo {
      return ValuesOrderInfo(call, emptyMap(), emptyMap())
    }
  }
}