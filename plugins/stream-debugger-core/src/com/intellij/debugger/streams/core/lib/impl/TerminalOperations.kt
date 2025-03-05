// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.core.lib.impl

import com.intellij.debugger.streams.core.resolve.IdentityResolver
import com.intellij.debugger.streams.core.trace.impl.handler.unified.ToCollectionHandler
import com.intellij.debugger.streams.core.trace.impl.interpret.CollectIdentityTraceInterpreter

/**
 * @author Vitaliy.Bibaev
 */

class ToCollectionOperation(name: String)
  : TerminalOperationBase(name, { call, _, dsl -> ToCollectionHandler(call, dsl) },
                          CollectIdentityTraceInterpreter(), IdentityResolver())