// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger.pydev

class InterruptDebugConsoleCommand(debugger: RemoteDebugger) : AbstractCommand<Any>(debugger, INTERRUPT_DEBUG_CONSOLE) {
  override fun buildPayload(payload: Payload?) {}
}