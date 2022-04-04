// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.breakpoint.new_arch.lib

import com.sun.jdi.Value

/**
 * @author Shumaf Lovpache
 */
interface RuntimeTraceHandler {
  val result: Value?
}