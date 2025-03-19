// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.impl.handler.unified

import com.intellij.debugger.streams.core.trace.dsl.Dsl
import com.intellij.debugger.streams.core.trace.impl.handler.unified.DistinctByKeyHandler
import com.intellij.debugger.streams.core.wrapper.IntermediateStreamCall

/**
 * @author Vitaliy.Bibaev
 */

class DistinctKeysHandler(callNumber: Int, call: IntermediateStreamCall, dsl: Dsl)
  : DistinctByKeyHandler.DistinctByCustomKey(callNumber, call, "java.util.function.Function<java.util.Map.Entry, java.lang.Object>",
                                             dsl.lambda("x") {
                                               doReturn(lambdaArg.call("getKey"))
                                             }.toCode(),
                                             dsl)

class DistinctValuesHandler(callNumber: Int, call: IntermediateStreamCall, dsl: Dsl)
  : DistinctByKeyHandler.DistinctByCustomKey(callNumber, call, "java.util.function.Function<java.util.Map.Entry, java.lang.Object>",
                                             dsl.lambda("x") {
                                               doReturn(lambdaArg.call("getValue"))
                                             }.toCode(),
                                             dsl)
