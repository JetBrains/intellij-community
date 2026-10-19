@file:Suppress("TestFunctionName")

package com.intellij.mcpserver.toolsets

import com.intellij.mcpserver.GeneralMcpToolsetTestBase
import com.intellij.mcpserver.McpExpectedError
import com.intellij.mcpserver.settings.McpServerSettings
import com.intellij.mcpserver.toolsets.general.IdeScriptDecision
import com.intellij.mcpserver.toolsets.general.IdeScriptEngineService
import com.intellij.mcpserver.toolsets.general.IdeScriptToolset
import com.intellij.mcpserver.toolsets.general.PreparedIdeScript
import com.intellij.mcpserver.toolsets.general.prepareIdeScript
import com.intellij.mcpserver.toolsets.util.CommandExecutionRejectedException
import com.intellij.openapi.util.registry.Registry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.StringWriter
import java.util.UUID

class IdeScriptToolsetTest : GeneralMcpToolsetTestBase() {
  @Test
  fun toolset_is_off_by_default() {
    assertThat(IdeScriptToolset().isEnabled()).isFalse()
  }

  @Test
  fun toolset_is_on_when_the_registry_key_is_on() {
    val registryValue = Registry.get(IDE_SCRIPT_TOOLSET_REGISTRY_KEY)
    registryValue.setValue(true)
    try {
      assertThat(IdeScriptToolset().isEnabled()).isTrue()
    }
    finally {
      registryValue.resetToDefault()
    }
  }

  @Test
  fun toolset_is_on_in_the_unattended_mode() {
    unattended {
      assertThat(IdeScriptToolset().isEnabled()).isTrue()
    }
  }

  @Test
  fun script_fails_without_brave_mode() = runBlocking<Unit>(Dispatchers.Default) {
    val prompts = Prompts(IdeScriptDecision.ALLOW)
    val error = withBraveMode(false) { runCatching { prepare("1 + 1", prompts) }.exceptionOrNull() }
    assertThat(error).isInstanceOf(McpExpectedError::class.java).hasMessageContaining("Brave Mode is off")
    assertThat(prompts.texts).isEmpty()
  }

  @Test
  fun script_asks_the_user_in_brave_mode() = runBlocking<Unit>(Dispatchers.Default) {
    val prompts = Prompts(IdeScriptDecision.ALLOW)
    val prepared = withBraveMode(true) { prepare("42", prompts) }
    assertThat(prepared.text).isEqualTo("42")
    assertThat(prompts.texts).containsExactly("42")
    assertThat(prompts.canAllowForSession).containsExactly(true)
  }

  @Test
  fun script_asks_again_after_allow_once() = runBlocking<Unit>(Dispatchers.Default) {
    val prompts = Prompts(IdeScriptDecision.ALLOW)
    val sessionId = newSessionId()
    withBraveMode(true) {
      prepare("1", prompts, sessionId)
      prepare("2", prompts, sessionId)
    }
    assertThat(prompts.texts).containsExactly("1", "2")
  }

  @Test
  fun script_allows_the_rest_of_the_session() = runBlocking<Unit>(Dispatchers.Default) {
    val prompts = Prompts(IdeScriptDecision.ALLOW_FOR_SESSION)
    val sessionId = newSessionId()
    withBraveMode(true) {
      prepare("1", prompts, sessionId)
      prepare("2", prompts, sessionId)
      prepare("3", prompts, sessionId)
      assertThat(prompts.texts).containsExactly("1")

      // A new session does not inherit the consent
      prepare("4", prompts, newSessionId())
    }
    assertThat(prompts.texts).containsExactly("1", "4")
  }

  @Test
  fun script_without_a_session_cannot_allow_the_session() = runBlocking<Unit>(Dispatchers.Default) {
    val prompts = Prompts(IdeScriptDecision.ALLOW_FOR_SESSION)
    withBraveMode(true) {
      prepare("1", prompts, sessionId = null)
      prepare("2", prompts, sessionId = null)
    }
    assertThat(prompts.texts).containsExactly("1", "2")
    assertThat(prompts.canAllowForSession).containsExactly(false, false)
  }

  @Test
  fun script_does_not_run_when_the_user_denies_it() = runBlocking<Unit>(Dispatchers.Default) {
    val error = withBraveMode(true) {
      runCatching { prepare("1 + 1", Prompts(IdeScriptDecision.DENY)) }.exceptionOrNull()
    }
    assertThat(error).isInstanceOf(CommandExecutionRejectedException::class.java)
  }

  @Test
  fun script_runs_unattended_without_brave_mode_and_confirmation() = runBlocking<Unit>(Dispatchers.Default) {
    val prompts = Prompts(IdeScriptDecision.DENY)
    val prepared = unattended { withBraveMode(false) { prepare("val x=1\nx", prompts) } }
    // The unattended mode runs the text of the agent as it is
    assertThat(prepared.text).isEqualTo("val x=1\nx")
    assertThat(prepared.formattedScript).isNull()
    assertThat(prompts.texts).isEmpty()
  }

  @Test
  fun script_drops_blank_lines_at_the_edges() = runBlocking<Unit>(Dispatchers.Default) {
    val prompts = Prompts(IdeScriptDecision.ALLOW)
    val prepared = withBraveMode(true) { prepare("\n  \r\n42\n\n", prompts) }
    assertThat(prepared.text).isEqualTo("42")
    assertThat(prepared.formattedScript).isNull()
    assertThat(prompts.texts).containsExactly("42")
  }

  @Test
  fun script_is_formatted_before_the_confirmation() = runBlocking<Unit>(Dispatchers.Default) {
    val prompts = Prompts(IdeScriptDecision.ALLOW)
    val prepared = withBraveMode(true) { prepare("val x=listOf(1,2)\nx.sum()", prompts) }
    assertThat(prepared.text).isEqualTo("val x = listOf(1, 2)\nx.sum()")
    assertThat(prepared.formattedScript).isEqualTo(prepared.text)
    assertThat(prompts.texts).containsExactly(prepared.text)
  }

  @Test
  fun engine_returns_the_last_expression() = runBlocking<Unit>(Dispatchers.Default) {
    assertThat(eval("1 + 1")).isEqualTo(2)
  }

  @Test
  fun engine_binds_the_project() = runBlocking<Unit>(Dispatchers.Default) {
    assertThat(eval("project.name")).isEqualTo(project.name)
  }

  /**
   * Answers each confirmation with [decision] and keeps the arguments of the confirmations.
   */
  private class Prompts(private val decision: IdeScriptDecision) {
    val texts: MutableList<String> = ArrayList()
    val canAllowForSession: MutableList<Boolean> = ArrayList()

    fun confirm(script: String, canAllow: Boolean): IdeScriptDecision {
      texts.add(script)
      canAllowForSession.add(canAllow)
      return decision
    }
  }

  private suspend fun prepare(script: String, prompts: Prompts, sessionId: String? = newSessionId()): PreparedIdeScript {
    return prepareIdeScript(project, sessionId, script) { text, canAllow -> prompts.confirm(text, canAllow) }
  }

  private suspend fun eval(script: String): Any? {
    return IdeScriptEngineService.getInstance().evalAsync(project, newSession = false, script, StringWriter()).await()
  }

  // The consents of the sessions stay in an application service, so each test uses new session IDs.
  private fun newSessionId(): String = UUID.randomUUID().toString()

  private inline fun <T> withBraveMode(enabled: Boolean, action: () -> T): T {
    val settings = McpServerSettings.getInstance()
    val original = settings.enableBraveMode
    settings.enableBraveMode = enabled
    try {
      return action()
    }
    finally {
      settings.enableBraveMode = original
    }
  }

  private inline fun <T> unattended(action: () -> T): T {
    System.setProperty(UNATTENDED_PROPERTY, "true")
    try {
      return action()
    }
    finally {
      System.clearProperty(UNATTENDED_PROPERTY)
    }
  }
}

private const val IDE_SCRIPT_TOOLSET_REGISTRY_KEY = "mcp.ide.script.toolset.enabled"
private const val UNATTENDED_PROPERTY = "idea.mcp.server.ide.script.unattended"
