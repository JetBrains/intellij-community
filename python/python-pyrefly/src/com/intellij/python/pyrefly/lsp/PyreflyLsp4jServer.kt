package com.intellij.python.pyrefly.lsp

import com.intellij.platform.lsp.api.Lsp4jServer
import org.eclipse.lsp4j.MarkupContent
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
import java.util.concurrent.CompletableFuture

/**
 * Pyrefly Type Server Protocol (TSP) endpoints.
 *
 * @see <a href="https://github.com/microsoft/pylance-release/blob/main/docs/tsp/typeServerProtocol.ts">Type Server Protocol</a>
 */
interface PyreflyLsp4jServer : Lsp4jServer {
  /**
   * Requests a type for a given element range.
   */
  @JsonRequest("types/provide-type")
  fun provideType(text_document: TextDocumentIdentifier, positions: List<Position>): CompletableFuture<ProvideTypeResponse>

  @JsonRequest("typeServer/getSnapshot")
  fun getSnapshot(): CompletableFuture<Int>

  @JsonRequest("typeServer/getComputedType")
  fun getComputedType(params: GetComputedTypeParams): CompletableFuture<TspType?>

  data class ProvideTypeResponse(val contents: List<MarkupContent>)

  data class TspNode(val uri: String, val range: Range)

  data class GetComputedTypeParams(val arg: TspNode, val snapshot: Int)

  data class TspDeclaration(
    val kind: String? = null,
    val name: String? = null,
    val node: TspNode? = null,
    val uri: String? = null,
  )

  data class TspSpecializedFunctionTypes(
    val parameterTypes: List<TspType>? = null,
    val returnType: TspType? = null,
  )

  data class TspType(
    val kind: String? = null,
    val name: String? = null,
    val moduleName: String? = null,
    val uri: String? = null,
    val stubContent: String? = null,
    val declaration: TspDeclaration? = null,
    val typeArgs: List<TspType>? = null,
    val subTypes: List<TspType>? = null,
    val overloads: List<TspType>? = null,
    val implementation: TspType? = null,
    val returnType: TspType? = null,
    val specializedTypes: TspSpecializedFunctionTypes? = null,
    val boundToType: TspType? = null,
    /**
     * `TypeFlags` bitfield from the TSP protocol. Bit 3 (`0x8`) is `LITERAL` and indicates
     * the type carries a literal value in [literalValue]. See `tsp_types::TypeFlags` upstream
     * for the full set of bits.
     */
    val flags: Int? = null,
    /**
     * For literal types (flag `LITERAL` set), the literal payload — an `Int`, `Boolean`, or
     * `String` after Gson-deserialization of pyrefly's untagged `LiteralValue` enum. Enum
     * / sentinel literals come through as JSON objects and we treat them as `null` for now.
     */
    val literalValue: Any? = null,
  )

  companion object {
    /** Bit 3 of `TypeFlags` — set when the type wraps a literal value. */
    const val LITERAL_FLAG: Int = 0x8
  }
}