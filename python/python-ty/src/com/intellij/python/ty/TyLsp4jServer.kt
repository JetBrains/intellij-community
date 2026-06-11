package com.intellij.python.ty

import com.intellij.platform.lsp.api.Lsp4jServer
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
import java.util.concurrent.CompletableFuture

/**
 * LSP4J interface for Ty custom endpoints.
 */
internal interface TyLsp4jServer : Lsp4jServer {
  /**
   * Requests a type for a given element range.
   * Endpoint: "types/provide-type"
   *
   * The server is expected to return a JSON object, e.g. { "ty": "fully.qualified.Type[Arg]" }
   */
  @JsonRequest("types/provide-type")
  fun provideType(params: Map<String, Any>): CompletableFuture<Map<String, Any? >>
}
