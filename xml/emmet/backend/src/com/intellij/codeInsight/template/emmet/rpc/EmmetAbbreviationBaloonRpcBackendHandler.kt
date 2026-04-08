// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.emmet.rpc

import com.intellij.codeInsight.template.emmet.ZenCodingTemplate
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.impl.EditorId
import com.intellij.platform.rpc.backend.RemoteApiProvider
import fleet.rpc.remoteApiDescriptor

private val log = logger<EmmetAbbreviationBaloonRpcBackendHandler>()

internal class EmmetAbbreviationBaloonRpcBackendHandler : EmmetAbbreviationBaloonRpc {
  override suspend fun isValid(transactionId: Int, editorId: EditorId): Boolean = readAction {
    EmmetAbbreviationBaloonTopic.invocationContext(transactionId, editorId) != null
  }

  override suspend fun isValidTemplateKey(
    transactionId: Int,
    editorId: EditorId,
    abbreviation: String,
  ): Boolean = readAction {
    val invocationContext = EmmetAbbreviationBaloonTopic.invocationContext(transactionId, editorId) ?: run {
      log.warn("template check failed for $abbreviation: cannot find invocation context for transactionId: $transactionId, editorId: $editorId")
      return@readAction false
    }
    ZenCodingTemplate.checkTemplateKey(abbreviation, invocationContext.customTemplateCallback);
  }

  override suspend fun enter(transactionId: Int, editorId: EditorId, abbreviation: String) {
    writeIntentReadAction {
      val invocationContext = EmmetAbbreviationBaloonTopic.invocationContext(transactionId, editorId) ?: run {
        log.warn("enter failed for $abbreviation: cannot find invocation context for transactionId: $transactionId, editorId: $editorId")
        return@writeIntentReadAction
      }
      invocationContext.callback.onEnter(abbreviation)
      EmmetAbbreviationBaloonTopic.dropInvocationContext(transactionId, editorId)
    }
  }

  override suspend fun cancel(transactionId: Int, editorId: EditorId) =
    EmmetAbbreviationBaloonTopic.dropInvocationContext(transactionId, editorId)
}

internal class EmmetAbbreviationBaloonRpcBackendProvider : RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() =
    remoteApi(remoteApiDescriptor<EmmetAbbreviationBaloonRpc>(), ::EmmetAbbreviationBaloonRpcBackendHandler)
}
