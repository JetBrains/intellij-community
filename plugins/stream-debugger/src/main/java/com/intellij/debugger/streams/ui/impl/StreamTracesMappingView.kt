package com.intellij.debugger.streams.ui.impl

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.streams.ui.TraceController

/**
 * @author Vitaliy.Bibaev
 */
class StreamTracesMappingView(
  evaluationContext: EvaluationContextImpl,
  prevController: TraceController,
  nextController: TraceController) : FlatView(listOf(prevController, nextController), evaluationContext) {
}