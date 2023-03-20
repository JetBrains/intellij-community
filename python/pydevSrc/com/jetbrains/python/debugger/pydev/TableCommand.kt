// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger.pydev

class TableCommand constructor(debugger: RemoteDebugger?,
                               threadId: String?,
                               frameId: String?,
                               private val initExpr: String,
                               private val commandType: TableCommandType) : AbstractFrameCommand<String?>(debugger,
                                                                                                          CMD_TABLE_EXEC,
                                                                                                          threadId,
                                                                                                          frameId) {
  var commandResult: String? = null

  override fun buildPayload(payload: Payload) {
    super.buildPayload(payload)
    payload.add(initExpr).add(commandType.name)
  }

  override fun isResponseExpected(): Boolean {
    return true
  }

  override fun processResponse(response: ProtocolFrame) {
    super.processResponse(response)
    commandResult = response.payload
  }
}

enum class TableCommandType {
  DF_INFO, SLICE
}