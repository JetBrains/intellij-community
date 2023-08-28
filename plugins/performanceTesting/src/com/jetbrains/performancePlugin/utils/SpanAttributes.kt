package com.jetbrains.performancePlugin.utils

import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder

object SpanAttributes {
  fun setWarmup(span: Span) {
      span.setAttribute("warmup", true)
  }
  fun setWarmup(span: SpanBuilder) {
      span.setAttribute("warmup", true)
  }
}

fun SpanBuilder.startSpanWithAttribute(isWarmUp: Boolean): Span {
  return this.startSpan().also {
    if (isWarmUp)
      SpanAttributes.setWarmup(it)
  }
}