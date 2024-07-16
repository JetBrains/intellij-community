// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger.pydev


/**
 * @param commandSequence the original [ProcessCreatedCommand] sequence number
 */
class ProcessCreatedMsgReceivedCommand(debugger: RemoteDebugger, private val commandSequence: Int)
  : AbstractCommand<Void>(debugger, PROCESS_CREATED_MSG_RECEIVED) {
  override fun buildPayload(payload: Payload) {
    payload.add(commandSequence)
  }
}