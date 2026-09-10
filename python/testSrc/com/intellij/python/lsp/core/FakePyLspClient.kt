// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.lsp.core

import com.intellij.openapi.editor.Document
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.Lsp4jServer
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.LspClientDescriptor
import com.intellij.platform.lsp.api.LspIntegrationProvider
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.python.ty.TyLspClientDescriptor
import com.intellij.python.ty.TyLspIntegrationProvider
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.TextDocumentIdentifier
import java.util.concurrent.CompletableFuture

/**
 * An [LspClient] that carries a descriptor and a state and nothing else. A test that only reads
 * [LspClient.descriptor] and [LspClient.state] needs no server process.
 */
internal fun fakePyLspClient(
  clientDescriptor: LspClientDescriptor,
  serverState: LspServerState = LspServerState.Running,
): LspClient = object : LspClient {
  override val providerClass: Class<out LspIntegrationProvider> = TyLspIntegrationProvider::class.java
  override val project: Project = clientDescriptor.project
  override val descriptor: LspClientDescriptor = clientDescriptor
  override val state: LspServerState = serverState
  override val initializeResult: InitializeResult? = null

  override fun sendNotification(lsp4jSender: (Lsp4jServer) -> Unit): Unit = unused()
  override suspend fun <R> sendRequest(lsp4jSender: (Lsp4jServer) -> CompletableFuture<R>): R? = null
  override fun <R> sendRequestSync(timeoutMs: Int, lsp4jSender: (Lsp4jServer) -> CompletableFuture<R>): R? = null
  override fun getDocumentIdentifier(file: VirtualFile): TextDocumentIdentifier = unused()
  override fun getDocumentVersion(document: Document): Int = unused()
  override fun nextDocumentVersion(document: Document): Int = unused()

  private fun unused(): Nothing = throw UnsupportedOperationException("The test only reads the descriptor")
}

/** A fake client of a Python LSP tool whose server answers for [servedModules] and is in [serverState]. */
internal fun fakePyToolClient(vararg servedModules: Module, serverState: LspServerState = LspServerState.Running): LspClient =
  fakePyLspClient(TyLspClientDescriptor(servedModules.first(), servedModules.toList()), serverState)
