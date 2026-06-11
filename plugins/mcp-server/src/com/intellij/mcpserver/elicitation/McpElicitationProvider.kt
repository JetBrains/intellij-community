package com.intellij.mcpserver.elicitation

import com.intellij.openapi.extensions.ExtensionPointName
import io.modelcontextprotocol.kotlin.sdk.server.ServerSession
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.serializer
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** Returns the applicable [McpElicitationProvider] for the current session, or `null`. */
suspend fun elicitationProvider(): McpElicitationProvider? {
  val kind = currentCoroutineContext()[McpSessionElement]?.elicitationKind ?: return null
  return McpElicitationProvider.EP.extensionList.firstOrNull { it.isApplicable(kind) }
}

/**
 * Strategy for satisfying an elicitation request for a given [McpElicitationKind].
 *
 * Implementations are contributed via [EP] and selected by [elicitationProvider] using
 * [isApplicable]. The built-in [McpElicitationCliProvider] handles CLI sessions over the MCP
 * transport; other environments (e.g. in-IDE AI chat with custom UI) can contribute their own.
 */
interface McpElicitationProvider {

  /** True if this provider can serve elicitation for a session of the given [kind]. */
  fun isApplicable(kind: McpElicitationKind): Boolean

  /**
   * Presents [form] to the user and decodes the accepted content via [deserializer].
   * Returns `null` if this provider cannot serve the current call (caller then falls back).
   */
  suspend fun <T> requestElicitation(
    form: ElicitationForm,
    deserializer: DeserializationStrategy<T>,
  ): ElicitationResult<T>?

  companion object {
    val EP: ExtensionPointName<McpElicitationProvider> =
      ExtensionPointName.create("com.intellij.mcpServer.elicitationProvider")
  }

}

suspend inline fun <reified T> McpElicitationProvider.requestElicitation(
  noinline block: ElicitationFormBuilder.() -> Unit,
): ElicitationResult<T>? =
  requestElicitation(buildElicitationForm(block), serializer<T>())

/**
 * Built-in [McpElicitationProvider] for CLI sessions: sends a real MCP `elicitation/create`
 * request to the client over the current session's [ServerSession] (read from the coroutine
 * context via [McpSessionElement]), maps the [ElicitationForm] to the SDK's `RequestedSchema`,
 * and decodes the `ElicitResult` into a typed [ElicitationResult].
 *
 * Returns `null` when there is no current session or the client did not advertise the
 * elicitation capability. In-IDE agents (AI chat, ACP, qodana, embedded) have no elicitation
 * UI yet, so this provider is applicable only for [McpElicitationKind.CLI].
 */
class McpElicitationCliProvider : McpElicitationProvider {

  override fun isApplicable(kind: McpElicitationKind): Boolean = kind == McpElicitationKind.CLI

  override suspend fun <T> requestElicitation(
    form: ElicitationForm,
    deserializer: DeserializationStrategy<T>,
  ): ElicitationResult<T>? {
    val session = currentCoroutineContext()[McpSessionElement]?.session ?: return null
    // checking client capabilities (it's not related to CLI/no-CLI checks)
    if (session.clientCapabilities?.elicitation == null) return null
    return session
      .createElicitation(form.message, form.toRequestedSchema())
      .toElicitationResult(deserializer)
  }
}

/**
 * A structured elicitation request: a [message] to show the user plus the [fields]
 * to collect. Build it with [buildElicitationForm].
 */
@ConsistentCopyVisibility
data class ElicitationForm internal constructor(
  val message: String,
  val fields: List<ElicitationField>,
)

/**
 * One field in an [ElicitationForm]. Each concrete variant maps to a primitive MCP
 * elicitation schema type (string, number, integer, boolean, enum). Created via the
 * builder DSL, not directly.
 */
sealed interface ElicitationField {
  val name: String
  val title: String?
  val description: String?
  val required: Boolean
}

/**
 * Outcome of an elicitation request. [T] is the type the accepted form content is
 * decoded into and must be `@Serializable`.
 */
sealed interface ElicitationResult<out T> {
  /** User submitted the form; [value] holds the decoded content. */
  data class Accept<T>(val value: T) : ElicitationResult<T>

  /** User explicitly declined the request. */
  data object Decline : ElicitationResult<Nothing>

  /** User dismissed the request without choosing. */
  data object Cancel : ElicitationResult<Nothing>
}

/**
 * Whether a session may receive MCP elicitation, fixed at session creation.
 *
 * - [CLI] — terminal / external clients: a real `elicitation/create` request is sent.
 * - [IDE] — in-IDE agents (AI chat, ACP, qodana, embedded): no elicitation UI yet, so callers fall back.
 */
enum class McpElicitationKind {
  CLI,
  IDE,
}

/**
 * Carries the current MCP session's [session] and [elicitationKind] in the coroutine context
 * for the duration of a tool call. Installed by the MCP session handler.
 * Read by [elicitationProvider] (for [elicitationKind]) and [McpElicitationCliProvider] (for [session]).
 * Internal: keeps the SDK [ServerSession] type out of the public API.
 */
internal class McpSessionElement(
  val session: ServerSession,
  val elicitationKind: McpElicitationKind,
) : AbstractCoroutineContextElement(McpSessionElement) {
  companion object Key : CoroutineContext.Key<McpSessionElement>
}
