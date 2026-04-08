// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.emmet.rpc

import com.intellij.openapi.editor.impl.EditorId
import com.intellij.platform.rpc.RemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor

@Rpc
interface EmmetAbbreviationBaloonRpc : RemoteApi<Unit> {
  companion object {
    suspend fun instance(): EmmetAbbreviationBaloonRpc = RemoteApiProviderService.resolve(remoteApiDescriptor<EmmetAbbreviationBaloonRpc>())
  }

  suspend fun cancel(transactionId: Int, editorId: EditorId)

  suspend fun isValid(transactionId: Int, editorId: EditorId): Boolean

  suspend fun isValidTemplateKey(transactionId: Int, editorId: EditorId, abbreviation: String): Boolean

  suspend fun enter(transactionId: Int, editorId: EditorId, abbreviation: String)
}