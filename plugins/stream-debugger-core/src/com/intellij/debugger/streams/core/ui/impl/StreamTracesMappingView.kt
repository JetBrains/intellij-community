// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.core.ui.impl

import com.intellij.debugger.streams.core.trace.CollectionTreeBuilder
import com.intellij.debugger.streams.core.trace.DebuggerCommandLauncher
import com.intellij.debugger.streams.core.trace.GenericEvaluationContext
import com.intellij.debugger.streams.core.ui.TraceController

/**
 * @author Vitaliy.Bibaev
 */
class StreamTracesMappingView(
  launcher: DebuggerCommandLauncher,
  context: GenericEvaluationContext,
  prevController: TraceController,
  nextController: TraceController,
  builder: CollectionTreeBuilder,
  debugName: String
) : FlatView(listOf(prevController, nextController), launcher, context, builder, debugName)