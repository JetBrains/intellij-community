// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.ui.impl

import com.intellij.debugger.streams.trace.CollectionTreeBuilder
import com.intellij.debugger.streams.trace.DebuggerCommandLauncher
import com.intellij.debugger.streams.ui.TraceController

/**
 * @author Vitaliy.Bibaev
 */
class StreamTracesMappingView(
  launcher: DebuggerCommandLauncher,
  prevController: TraceController,
  nextController: TraceController,
  builder: CollectionTreeBuilder,
  debugName: String
) : FlatView(listOf(prevController, nextController), launcher, builder, debugName)