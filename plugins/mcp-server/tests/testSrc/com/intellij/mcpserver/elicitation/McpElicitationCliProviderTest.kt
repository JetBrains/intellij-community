package com.intellij.mcpserver.elicitation

import com.intellij.mcpserver.HttpTransportHolder
import com.intellij.mcpserver.McpTool
import com.intellij.mcpserver.McpToolsProvider
import com.intellij.mcpserver.McpToolsetTestBase
import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.elicitation.ElicitationResult.Accept
import com.intellij.mcpserver.elicitation.ElicitationResult.Cancel
import com.intellij.mcpserver.elicitation.ElicitationResult.Decline
import com.intellij.mcpserver.impl.McpServerService
import com.intellij.mcpserver.impl.util.asTool
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.use
import com.intellij.util.application
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.ClientOptions
import io.modelcontextprotocol.kotlin.sdk.types.ClientCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.ElicitRequest
import io.modelcontextprotocol.kotlin.sdk.types.ElicitRequestFormParams
import io.modelcontextprotocol.kotlin.sdk.types.ElicitRequestParams.RequestedSchema
import io.modelcontextprotocol.kotlin.sdk.types.ElicitResult
import io.modelcontextprotocol.kotlin.sdk.types.EmptyJsonObject
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.TitledMultiSelectEnumSchema
import io.modelcontextprotocol.kotlin.sdk.types.UntitledSingleSelectEnumSchema
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds

/**
 * Drives a real MCP client <-> server session and verifies that [McpElicitationCliProvider], invoked from
 * inside a tool call, performs the full `elicitation/create` roundtrip, decodes the response into a
 * typed [ElicitationResult], and is gated to CLI-mode sessions.
 *
 * Extends [McpToolsetTestBase] to reuse its `authorizedSession`-backed [withConnection] (the IDE
 * elicitation kind). CLI-kind cases use the shared global server via [cliElicitationTest].
 */
class McpElicitationCliProviderTest : McpToolsetTestBase() {

  @Serializable
  data class ConfirmInput(val confirm: Boolean = false)

  // The tool captures the provider's return into this deferred instead of returning it as the tool
  // result: that keeps the typed ElicitationResult<ConfirmInput> intact (exact Accept/Decline/Cancel
  // and decoded payload), whereas a tool return would be serialized to CallToolResult and lose it.
  private val confirmationResult = CompletableDeferred<ElicitationResult<ConfirmInput>?>()

  private val elicitationTool = this@McpElicitationCliProviderTest::request_elicitation.asTool()

  @com.intellij.mcpserver.annotations.McpTool
  @McpDescription("Asks the client to confirm via MCP elicitation")
  suspend fun request_elicitation() {
    confirmationResult.complete(
      elicitationProvider()?.requestElicitation<ConfirmInput> {
        message = "Confirm?"
        booleanField("confirm") { required = true }
      }
    )
  }

  @Test
  fun accept_is_decoded_into_typed_result() =
    cliElicitationTest(
      capabilities = ClientCapabilities(elicitation = EmptyJsonObject),
      elicitHandler = { ElicitResult(ElicitResult.Action.Accept, buildJsonObject { put("confirm", true) }) },
    ) {
      val result = callAndAwait(elicitationTool, confirmationResult)
      assertThat(result).isEqualTo(Accept(ConfirmInput(confirm = true)))
    }

  @Test
  fun decline_is_mapped() =
    cliElicitationTest(
      capabilities = ClientCapabilities(elicitation = EmptyJsonObject),
      elicitHandler = { ElicitResult(ElicitResult.Action.Decline) },
    ) {
      val result = callAndAwait(elicitationTool, confirmationResult)
      assertThat(result).isEqualTo(Decline)
    }

  @Test
  fun cancel_is_mapped() =
    cliElicitationTest(
      capabilities = ClientCapabilities(elicitation = EmptyJsonObject),
      elicitHandler = { ElicitResult(ElicitResult.Action.Cancel) },
    ) {
      val result = callAndAwait(elicitationTool, confirmationResult)
      assertThat(result).isEqualTo(Cancel)
    }

  @Test
  fun returns_null_when_client_lacks_elicitation_capability() =
    cliElicitationTest(
      capabilities = ClientCapabilities(),
      elicitHandler = null,
    ) {
      val result = callAndAwait(elicitationTool, confirmationResult)
      assertThat(result).isNull()
    }

  @Test
  fun returns_null_for_in_ide_session_even_with_capable_client() = runBlocking(Dispatchers.Default) {
    // withConnection runs on the isolated IDE server (IDE elicitation kind) -> provider must not elicit,
    // even though the client fully supports elicitation.
    val capableClient = Client(
      Implementation(name = "test client", version = "1.0"),
      ClientOptions(capabilities = ClientCapabilities(elicitation = EmptyJsonObject)),
    )
    capableClient.setElicitationHandler { ElicitResult(ElicitResult.Action.Accept, buildJsonObject { put("confirm", true) }) }

    withConnection(capableClient) { client ->
      val result = client.callAndAwait(elicitationTool, confirmationResult)
      assertThat(result).isNull()
    }
  }

  /** Mirrors every primitive elicitation field kind so the full schema mapping is exercised. */
  @Serializable
  data class ComplexInput(
    val text: String = "",
    val count: Int = 0,
    val ratio: Double = 0.0,
    val enabled: Boolean = false,
    val color: String = "",
    val tags: List<String> = emptyList(),
  )

  private val complexResult = CompletableDeferred<ElicitationResult<ComplexInput>?>()

  @com.intellij.mcpserver.annotations.McpTool
  @McpDescription("Requests a form with every elicitation field kind")
  suspend fun request_complex_elicitation() {
    complexResult.complete(
      elicitationProvider()?.requestElicitation<ComplexInput> {
        message = "All field kinds"
        stringField("text") {
          title = "Text"
          description = "Free text"
          required = true
          minLength = 1
          maxLength = 20
        }
        integerField("count") { minimum = 0; maximum = 100; default = 1 }
        numberField("ratio") { minimum = 0.0; maximum = 1.0 }
        booleanField("enabled") { default = true }
        singleSelect("color") { option("red"); option("green"); option("blue") }   // untitled enum
        multiSelect("tags") { option("a", "A"); option("b", "B") }            // titled enum
      }
    )
  }

  @Test
  fun complex_form_maps_all_field_kinds() {
    val capturedSchema = CompletableDeferred<RequestedSchema>()
    cliElicitationTest(
      capabilities = ClientCapabilities(elicitation = EmptyJsonObject),
      elicitHandler = { request ->
        capturedSchema.complete((request.params as ElicitRequestFormParams).requestedSchema)
        val clientResponse = buildJsonObject {
          put("text", "Neo")
          put("count", 42)
          put("ratio", 0.5)
          put("enabled", true)
          put("color", "green")
          putJsonArray("tags") { add("a"); add("b") }
        }
        ElicitResult(ElicitResult.Action.Accept, clientResponse)
      },
    ) {
      val complexElicitationTool = this@McpElicitationCliProviderTest::request_complex_elicitation.asTool()
      val result = callAndAwait(complexElicitationTool, complexResult)
      assertThat(result).isEqualTo(
        Accept(ComplexInput(text = "Neo", count = 42, ratio = 0.5, enabled = true, color = "green", tags = listOf("a", "b")))
      )

      // Captured schema reflects the typed DSL mapping.
      val schema = capturedSchema.await()
      assertThat(schema.required).containsExactly("text")
      assertThat(schema.properties.keys).containsExactlyInAnyOrder("text", "count", "ratio", "enabled", "color", "tags")
      assertThat(schema.properties["color"]).isInstanceOf(UntitledSingleSelectEnumSchema::class.java)
      assertThat(schema.properties["tags"]).isInstanceOf(TitledMultiSelectEnumSchema::class.java)
    }
  }


  /** Registers [tool] on the MCP server, invokes it, and returns the [ElicitationResult] it captured. */
  private suspend fun <T> Client.callAndAwait(tool: McpTool, resultHolder: CompletableDeferred<T>): T {
    delay(TOOL_REFRESH_DELAY)
    Disposer.newDisposable().use { disposable ->
      application.extensionArea
        .getExtensionPoint(McpToolsProvider.EP)
        .registerExtension(
          object : McpToolsProvider { override fun getTools(): List<McpTool> = listOf(tool) },
          disposable
        )
      // tool list changes are processed in a background coroutine, so wait before/after
      delay(TOOL_REFRESH_DELAY)
      callTool(tool.descriptor.name, emptyMap())
      return withTimeout(TOOL_RESULT_TIMEOUT) { resultHolder.await() }
    }
  }

  /** Shared (global) server -> CLI elicitation kind. */
  private fun cliElicitationTest(
    capabilities: ClientCapabilities,
    elicitHandler: ((ElicitRequest) -> ElicitResult)?,
    action: suspend Client.() -> Unit,
  ) = runBlocking(Dispatchers.Default) {
    val transportHolder = HttpTransportHolder(project)
    try {
      McpServerService.getInstance().start()
      val client = Client(
        Implementation(name = "test client", version = "1.0"),
        ClientOptions(capabilities = capabilities)
      )
      if (elicitHandler != null) client.setElicitationHandler(elicitHandler)
      client.connect(transportHolder.transport)
      client.action()
    }
    finally {
      transportHolder.close()
      McpServerService.getInstance().stop()
    }
  }

  companion object {
    private val TOOL_REFRESH_DELAY = 500.milliseconds
    private val TOOL_RESULT_TIMEOUT = 2000.milliseconds
  }
}