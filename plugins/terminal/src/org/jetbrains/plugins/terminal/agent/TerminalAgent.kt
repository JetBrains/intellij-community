// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.agent

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.NlsActions
import com.intellij.platform.eel.EelOsFamily
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.TerminalBundle
import org.jetbrains.plugins.terminal.TerminalIcons
import javax.swing.Icon

const val TERMINAL_AI_AGENTS_REGISTRY_KEY: String = "terminal.agent.predefined.actions.enabled"
private val DEFAULT_WINDOWS_EXECUTABLE_EXTENSIONS: List<String> = listOf("exe", "bat", "cmd", "ps1")

@ApiStatus.Internal
interface TerminalAgent {

  @Serializable
  @JvmInline
  value class AgentKey(val key: String)

  val agentKey: AgentKey
  val displayName: @NlsActions.ActionText String
  val installActionText: @NlsActions.ActionText String?
    get() = null
  val secondaryText: String?
    get() = null
  val showsNewBadge: Boolean
    get() = false

  val binaryName: String
  /**
   * Ordered directories to probe after PATH lookup.
   * Use the `$HOME` marker for home-relative entries (for example, `$HOME/.local/bin`
   * or `$HOME\AppData\Roaming\npm`). Entries without `$HOME` are treated as absolute paths.
   */
  val posixKnownLocationCandidates: List<String>
    get() = emptyList()

  /**
   * Ordered directories to probe after PATH lookup.
   * Use the `$HOME` marker for home-relative entries (for example, `$HOME/.local/bin`
   * or `$HOME\AppData\Roaming\npm`). Entries without `$HOME` are treated as absolute paths.
   */
  val windowsKnownLocationCandidates: List<String>
    get() = emptyList()

  /**
   * Ordered executable extensions to probe on Windows for both PATH lookup and known-location lookup.
   */
  val windowsExecutableExtensions: List<String>
    get() = DEFAULT_WINDOWS_EXECUTABLE_EXTENSIONS
  val installCommandUnix: List<String>?
    get() = null
  val installCommandWindows: List<String>?
    get() = null

  val icon: Icon?
  val showIconInTab: Boolean get() = true

  fun getInstallCommand(osFamily: EelOsFamily): List<String>? {
    return when (osFamily) {
      EelOsFamily.Windows -> installCommandWindows
      EelOsFamily.Posix -> installCommandUnix
    }
  }

  companion object {
    @JvmStatic
    fun getAllProviders(): List<TerminalAgentProvider> = TerminalAgentProvider.EP_NAME.extensionList

    @JvmStatic
    fun getAllTerminalAgents(): List<TerminalAgent> = flattenProviders(getAllProviders())

    @JvmStatic
    fun findByKey(agentKey: AgentKey?): TerminalAgent? {
      return agentKey?.let { key -> getAllTerminalAgents().firstOrNull { it.agentKey == key } }
    }

    @JvmStatic
    fun flattenProviders(providers: List<TerminalAgentProvider>): List<TerminalAgent> {
      val uniqueAgents = LinkedHashMap<AgentKey, TerminalAgent>()
      for (provider in providers) {
        for (nextAgent in provider.getTerminalAgents()) {
          uniqueAgents.putIfAbsent(nextAgent.agentKey, nextAgent)
        }
      }
      return uniqueAgents.values.toList()
    }
  }
}

@ApiStatus.Internal
interface TerminalAgentProvider {
  fun getTerminalAgents(): List<TerminalAgent>

  companion object {
    val EP_NAME: ExtensionPointName<TerminalAgentProvider> = ExtensionPointName.create(
      "org.jetbrains.plugins.terminal.terminalAgentProvider"
    )
  }
}

@ApiStatus.Internal
class DefaultTerminalAgentProvider : TerminalAgentProvider {
  override fun getTerminalAgents(): List<TerminalAgent> {
    return listOf(JunieTerminalAgent, ClaudeCodeTerminalAgent, CodexTerminalAgent)
  }
}

private abstract class BundledTerminalAgent(
  override val agentKey: TerminalAgent.AgentKey,
  override val displayName: @NlsActions.ActionText String,
  override val binaryName: String,
  override val posixKnownLocationCandidates: List<String>,
  override val windowsKnownLocationCandidates: List<String>,
  override val windowsExecutableExtensions: List<String> = DEFAULT_WINDOWS_EXECUTABLE_EXTENSIONS,
  override val icon: Icon,
) : TerminalAgent

private object ClaudeCodeTerminalAgent : BundledTerminalAgent(
  agentKey = TerminalAgent.AgentKey("claude_code"),
  displayName = TerminalBundle.message("terminal.aiAgents.claudeCode.displayName"),
  binaryName = "claude",
  posixKnownLocationCandidates = listOf(
    $$"$HOME/.local/bin",
    "/usr/local/bin"
  ),
  windowsKnownLocationCandidates = listOf(
    $$"$HOME\\AppData\\Roaming\\npm",
    $$"$HOME\\.local\\bin"
  ),
  icon = TerminalIcons.Agents.ClaudeCode,
) {
  override val showIconInTab: Boolean = false // Claude Code shows its own icon as a text symbol
}

private object CodexTerminalAgent : BundledTerminalAgent(
  agentKey = TerminalAgent.AgentKey("codex"),
  displayName = TerminalBundle.message("terminal.aiAgents.codex.displayName"),
  binaryName = "codex",
  posixKnownLocationCandidates = listOf(
    $$"$HOME/.local/bin",
    "/usr/local/bin"
  ),
  windowsKnownLocationCandidates = listOf(
    $$"$HOME\\AppData\\Roaming\\npm"
  ),
  icon = TerminalIcons.Agents.Codex,
)

private object JunieTerminalAgent : BundledTerminalAgent(
  agentKey = TerminalAgent.AgentKey("junie"),
  displayName = TerminalBundle.message("terminal.aiAgents.junie.displayName"),
  binaryName = "junie",
  posixKnownLocationCandidates = listOf($$"$HOME/.local/bin"),
  windowsKnownLocationCandidates = listOf($$"$HOME\\.local\\bin"),
  windowsExecutableExtensions = listOf("bat"),
  icon = TerminalIcons.Agents.Junie,
) {
  override val installActionText: String
    get() = TerminalBundle.message("terminal.aiAgents.junie.installActionText")

  override val secondaryText: String
    get() = TerminalBundle.message("terminal.aiAgents.junie.secondaryText")

  override val showsNewBadge: Boolean
    get() = true

  override val installCommandUnix: List<String> = listOf(
    "/bin/bash", "-c",
    $$"curl -fsSL https://junie.jetbrains.com/install.sh | /bin/bash && exec \"$HOME/.local/bin/junie\"",
  )

  override val installCommandWindows: List<String> = listOf(
    "powershell.exe",
    "-NoProfile",
    "-ExecutionPolicy", "Bypass",
    "-Command",
    $$"iex (irm 'https://junie.jetbrains.com/install.ps1'); & \"$HOME\\.local\\bin\\junie.bat\"",
  )
}
