// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.ui.impl

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.streams.ui.TraceController

/**
 * @author Vitaliy.Bibaev
 */
class StreamTracesMappingView(
  evaluationContext: EvaluationContextImpl,
  prevController: TraceController,
  nextController: TraceController) : FlatView(listOf(prevController, nextController), evaluationContext)