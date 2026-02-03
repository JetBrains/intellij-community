// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger.pydev

import com.intellij.util.asSafely
import com.jetbrains.python.debugger.pydev.tables.PyDevImageCommandParameters
import com.jetbrains.python.tables.TableCommandParameters
import com.jetbrains.python.tables.TableCommandType

class TableImageCommand(
  debugger: RemoteDebugger?, threadId: String?, frameId: String?,
  private val initExpr: String,
  private val commandType: TableCommandType,
  private val tableCommandParameters: TableCommandParameters?
) : AbstractFrameCommand<String?>(debugger, getCommandCode(tableCommandParameters), threadId, frameId) {
  var commandResult: String? = null

  override fun buildPayload(payload: Payload) {
    super.buildPayload(payload)
    payload.add(initExpr).add(commandType.name)
    if (commandType == TableCommandType.IMAGE_CHUNK_LOAD) {
      addChunkParameters(payload)
    }
  }

  private fun addChunkParameters(payload: Payload) {
    tableCommandParameters?.asSafely<PyDevImageCommandParameters>()?.let {
      payload.add(it.offset ?: -1).add(it.imageId)
    }
  }

  override fun isResponseExpected(): Boolean = true

  override fun processResponse(response: ProtocolFrame) {
    super.processResponse(response)
    commandResult = response.payload
  }

  companion object {
    private fun getCommandCode(parameters: TableCommandParameters?): Int =
      if (parameters?.asSafely<PyDevImageCommandParameters>() == null) {
        IMAGE_COMMAND_START_LOAD
      }
      else {
        IMAGE_COMMAND_CHUNK_LOAD
      }
  }
}