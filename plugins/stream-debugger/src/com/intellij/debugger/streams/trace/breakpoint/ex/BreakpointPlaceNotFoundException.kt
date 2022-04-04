// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.breakpoint.ex

/**
 * @author Shumaf Lovpache
 */
class BreakpointPlaceNotFoundException(streamOperation: String) : BreakpointTracingException("Cannot find declarations for ${streamOperation} operation in stream chain")
