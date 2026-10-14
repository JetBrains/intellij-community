package com.intellij.mcpserver.elicitation

import com.intellij.concurrency.installThreadContext
import com.intellij.mcpserver.ClientInfo
import com.intellij.mcpserver.McpCallAdditionalDataElement
import com.intellij.mcpserver.McpCallHeaders
import com.intellij.mcpserver.McpCallInfo
import com.intellij.mcpserver.McpLaunchOrigin
import com.intellij.mcpserver.McpToolCategory
import com.intellij.mcpserver.McpToolDescriptor
import com.intellij.mcpserver.McpToolSchema
import com.intellij.mcpserver.impl.McpServerService
import com.intellij.mcpserver.launchOrigin
import kotlinx.serialization.json.JsonObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class McpAirChatElicitationProviderTest {
  @Test
  fun `the launch origin comes from the header of the call`() {
    assertThat(headers(McpLaunchOrigin.AIR_CHAT).launchOrigin).isEqualTo(McpLaunchOrigin.AIR_CHAT)
  }

  @Test
  fun `an empty or missing header has no launch origin`() {
    assertThat(headers("").launchOrigin).isNull()
    assertThat(McpCallHeaders(emptyMap()).launchOrigin).isNull()
    assertThat((null as McpCallHeaders?).launchOrigin).isNull()
  }

  @Test
  fun `a call from the Air chat goes to the Air chat provider, not to the CLI provider`() {
    withOrigin(McpLaunchOrigin.AIR_CHAT) {
      assertThat(McpAirChatElicitationProvider().isApplicable(McpElicitationKind.CLI)).isTrue()
      assertThat(McpElicitationCliProvider().isApplicable(McpElicitationKind.CLI)).isFalse()
    }
  }

  @Test
  fun `a call without the launch origin goes to the CLI provider, not to the Air chat provider`() {
    withOrigin(null) {
      assertThat(McpAirChatElicitationProvider().isApplicable(McpElicitationKind.CLI)).isFalse()
      assertThat(McpElicitationCliProvider().isApplicable(McpElicitationKind.CLI)).isTrue()
    }
  }

  private fun headers(origin: String): McpCallHeaders = McpCallHeaders(mapOf(McpLaunchOrigin.NAME to listOf(origin)))

  private fun withOrigin(origin: String?, block: () -> Unit) {
    val callInfo = McpCallInfo(
      callId = 1,
      clientInfo = ClientInfo("test client", "1.0"),
      project = null,
      mcpToolDescriptor = McpToolDescriptor(
        name = "test",
        description = "test",
        fullyQualifiedName = "test",
        category = McpToolCategory(shortName = "Test", fullyQualifiedName = "test", isExperimental = false),
        inputSchema = McpToolSchema.ofPropertiesSchema(JsonObject(emptyMap()), emptySet(), emptyMap()),
      ),
      rawArguments = JsonObject(emptyMap()),
      meta = JsonObject(emptyMap()),
      mcpSessionOptions = McpServerService.McpSessionOptions(McpServerService.AskCommandExecutionMode.DONT_ASK),
      headers = if (origin == null) McpCallHeaders(emptyMap()) else headers(origin),
    )
    installThreadContext(McpCallAdditionalDataElement(callInfo), action = block)
  }
}
