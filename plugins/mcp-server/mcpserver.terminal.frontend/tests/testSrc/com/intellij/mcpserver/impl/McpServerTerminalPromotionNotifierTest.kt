package com.intellij.mcpserver.impl

import com.intellij.mcpserver.clients.McpClient
import com.intellij.mcpserver.clients.McpClientInfo
import com.intellij.mcpserver.clients.configs.ExistingConfig
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

@TestApplication
internal class McpServerTerminalPromotionNotifierTest {
  @AfterEach
  fun resetState() {
    McpClient.overrideProductSpecificServerKeyForTests(null)
    McpServerTerminalPromotionDismissalState.reset()
  }

  @Test
  fun `matches codex command`() {
    assertThat(matchMcpServerTerminalProvider("codex --help")).isEqualTo(McpServerTerminalProvider.CODEX)
  }

  @Test
  fun `matches claude path command`() {
    assertThat(matchMcpServerTerminalProvider("/usr/local/bin/claude --resume"))
      .isEqualTo(McpServerTerminalProvider.CLAUDE)
  }

  @Test
  fun `matches windows executable suffix`() {
    assertThat(matchMcpServerTerminalProvider("C:\\Tools\\Codex.exe resume thread-1"))
      .isEqualTo(McpServerTerminalProvider.CODEX)
  }

  @Test
  fun `skips leading environment assignments`() {
    assertThat(matchMcpServerTerminalProvider("FOO=1 BAR=2 claude --print"))
      .isEqualTo(McpServerTerminalProvider.CLAUDE)
  }

  @Test
  fun `ignores unrelated command`() {
    assertThat(matchMcpServerTerminalProvider("git status")).isNull()
  }

  @Test
  fun `prefers project client when project config artifacts exist`() {
    McpClient.overrideProductSpecificServerKeyForTests("idea")
    val projectClient =
      FakeClient(McpClientInfo.Scope.Project("/fake/project"), linkedMapOf("idea" to ExistingConfig(url = "http://example.com:1/stream", type = "http")))
    val globalClient =
      FakeClient(McpClientInfo.Scope.Global, linkedMapOf("idea" to ExistingConfig(url = "http://example.com:2/stream", type = "http")))

    val preferred = McpServerTerminalPromotionCandidates(projectClient, globalClient).preferredClient()

    assertThat(preferred).isSameAs(projectClient)
  }

  @Test
  fun `prefers global client when project config is not attached`() {
    McpClient.overrideProductSpecificServerKeyForTests("idea")
    val projectClient =
      FakeClient(McpClientInfo.Scope.Project("/fake/project"),
                 linkedMapOf("other" to ExistingConfig(url = "http://example.com:1/stream", type = "http")),
                 false)
    val globalClient =
      FakeClient(McpClientInfo.Scope.Global,
                 linkedMapOf("idea" to ExistingConfig(url = "http://example.com:2/stream", type = "http")),
                 true)

    val preferred = McpServerTerminalPromotionCandidates(projectClient, globalClient).preferredClient()

    assertThat(preferred).isSameAs(globalClient)
  }

  @Test
  fun `falls back to project client when no server is configured`() {
    val projectClient = FakeClient(McpClientInfo.Scope.Project("/fake/project"), null, false)
    val globalClient = FakeClient(McpClientInfo.Scope.Global, null, false)

    val preferred = McpServerTerminalPromotionCandidates(projectClient, globalClient).preferredClient()

    assertThat(preferred).isSameAs(projectClient)
  }

  @Test
  fun `setup clients prefer project scope on the left`() {
    val projectClient = FakeClient(McpClientInfo.Scope.Project("/fake/project"), null, false)
    val globalClient = FakeClient(McpClientInfo.Scope.Global, null, false)

    val setupClients = McpServerTerminalPromotionCandidates(projectClient, globalClient).setupClients()

    assertThat(setupClients).containsExactly(projectClient, globalClient)
  }

  @Test
  fun `setup dropdown action text omits scope`() {
    assertThat(mcpServerTerminalPromotionSetupGroupActionText())
      .isEqualTo("Set up ${ApplicationNamesInfo.getInstance().fullProductName} MCP")
  }

  @Test
  fun `reconfigure action text uses fix configuration`() {
    assertThat(mcpServerTerminalPromotionFixActionText())
      .isEqualTo("Fix Configuration")
  }

  @Test
  fun `setup dropdown action group keeps project scope first`() {
    val projectClient = FakeClient(McpClientInfo.Scope.Project("/fake/project"), null, false)
    val globalClient = FakeClient(McpClientInfo.Scope.Global, null, false)

    val setupActionGroup = createMcpServerTerminalPromotionSetupActionGroup(listOf(projectClient, globalClient)) { }

    assertThat(setupActionGroup.getChildActionsOrStubs().map { it.templateText })
      .containsExactly(
        "Set up ${ApplicationNamesInfo.getInstance().fullProductName} MCP (project)",
        "Set up ${ApplicationNamesInfo.getInstance().fullProductName} MCP (global)",
      )
  }

  @Test
  fun `setup action text includes project scope`() {
    assertThat(mcpServerTerminalPromotionSetupActionText(McpClientInfo.Scope.Project("/fake/project")))
      .isEqualTo("Set up ${ApplicationNamesInfo.getInstance().fullProductName} MCP (project)")
  }

  @Test
  fun `setup action text includes global scope`() {
    assertThat(mcpServerTerminalPromotionSetupActionText(McpClientInfo.Scope.Global))
      .isEqualTo("Set up ${ApplicationNamesInfo.getInstance().fullProductName} MCP (global)")
  }

  @Test
  fun `reconfigure clients include every wrong port scope`() {
    McpClient.overrideProductSpecificServerKeyForTests("idea")
    val projectClient =
      FakeClient(McpClientInfo.Scope.Project("/fake/project"),
                 linkedMapOf("idea" to ExistingConfig(url = "http://localhost:63000/stream", type = "http")))
    val globalClient =
      FakeClient(McpClientInfo.Scope.Global,
                 linkedMapOf("idea" to ExistingConfig(url = "http://127.0.0.1:65000/stream", type = "http")))

    val reconfigureClients =
      McpServerTerminalPromotionCandidates(projectClient, globalClient).reconfigureClients(expectedPort = 64000)

    assertThat(reconfigureClients).containsExactly(projectClient, globalClient)
  }

  @Test
  fun `reconfigure clients skip scopes already on current port`() {
    McpClient.overrideProductSpecificServerKeyForTests("idea")
    val projectClient =
      FakeClient(McpClientInfo.Scope.Project("/fake/project"),
                 linkedMapOf("idea" to ExistingConfig(url = "http://localhost:63000/stream", type = "http")))
    val globalClient =
      FakeClient(McpClientInfo.Scope.Global,
                 linkedMapOf("idea" to ExistingConfig(url = "http://127.0.0.1:64000/stream", type = "http")))

    val reconfigureClients =
      McpServerTerminalPromotionCandidates(projectClient, globalClient).reconfigureClients(expectedPort = 64000)

    assertThat(reconfigureClients).containsExactly(projectClient)
  }

  @Test
  fun `active command tracker returns banner disposable when tracked command finishes`() {
    val tracker = McpServerTerminalPromotionActiveCommandTracker<String>()
    val bannerDisposable = Disposer.newCheckedDisposable()

    tracker.trackStartedCommand("codex-1")

    assertThat(tracker.registerBanner("codex-1", bannerDisposable)).isTrue()
    assertThat(tracker.markCommandFinished("codex-1")).isSameAs(bannerDisposable)

    Disposer.dispose(bannerDisposable)
  }

  @Test
  fun `active command tracker rejects banner when command already finished`() {
    val tracker = McpServerTerminalPromotionActiveCommandTracker<String>()
    val bannerDisposable = Disposer.newCheckedDisposable()

    tracker.trackStartedCommand("codex-1")
    assertThat(tracker.markCommandFinished("codex-1")).isNull()

    assertThat(tracker.registerBanner("codex-1", bannerDisposable)).isFalse()

    Disposer.dispose(bannerDisposable)
  }

  @Test
  fun `banner disposal keeps listener armed when terminal session stays active`() {
    assertThat(shouldKeepListen(
      isDismissed = false,
      isProjectDisposed = false,
      isViewDisposed = false,
    )).isTrue()
  }

  @Test
  fun `banner disposal stops listener when promotion was dismissed`() {
    assertThat(shouldKeepListen(
      isDismissed = true,
      isProjectDisposed = false,
      isViewDisposed = false,
    )).isFalse()
  }

  @Test
  fun `returns enable issue when server is disabled and client is not configured`() {
    assertThat(determineMcpServerTerminalPromotionIssue(isServerRunning = false,
                                                        isClientConfigured = false,
                                                        hasPromotionCandidateConfig = false,
                                                        isPortCorrect = true))
      .isEqualTo(McpServerTerminalPromotionIssue.ENABLE_SERVER)
  }

  @Test
  fun `returns configure issue when server is running and client is not configured`() {
    assertThat(determineMcpServerTerminalPromotionIssue(isServerRunning = true,
                                                        isClientConfigured = false,
                                                        hasPromotionCandidateConfig = false,
                                                        isPortCorrect = true))
      .isEqualTo(McpServerTerminalPromotionIssue.CONFIGURE_CLIENT)
  }

  @Test
  fun `returns reconfigure issue when port mismatches`() {
    assertThat(determineMcpServerTerminalPromotionIssue(isServerRunning = true,
                                                        isClientConfigured = true,
                                                        hasPromotionCandidateConfig = true,
                                                        isPortCorrect = false))
      .isEqualTo(McpServerTerminalPromotionIssue.RECONFIGURE_CLIENT)
  }

  @Test
  fun `returns no issue when setup is ready`() {
    assertThat(determineMcpServerTerminalPromotionIssue(isServerRunning = true,
                                                        isClientConfigured = true,
                                                        hasPromotionCandidateConfig = true,
                                                        isPortCorrect = true)).isNull()
  }

  @Test
  fun `do not show again gate persists dismissal`() {
    assertThat(McpServerTerminalPromotionDismissalState.isDismissed()).isFalse()

    McpServerTerminalPromotionDismissalState.dismiss()

    assertThat(McpServerTerminalPromotionDismissalState.isDismissed()).isTrue()
  }

  @Test
  fun `terminal descriptor registers mcp server listener`() {
    val descriptor = checkNotNull(javaClass.classLoader.getResource("intellij.mcpserver.terminal.frontend.xml")) {
      "MCP terminal frontend descriptor is missing"
    }.readText()

    assertThat(descriptor)
      .contains("McpServerTerminalPromotionNotifier")
      .contains("com.intellij.terminal.frontend.toolwindow.TerminalTabsManagerListener")
  }

  @Test
  fun `skips promotion when project mcp json contains ij-proxy entry`(@TempDir projectDir: Path) {
    projectDir.resolve(".mcp.json").writeText(
      """{"mcpServers": {"ij-proxy": {"command": "/bin/ij-proxy"}}}"""
    )
    assertThat(projectBaseDirHasIjProxyMcpServer(projectDir)).isTrue()
  }

  @Test
  fun `skips promotion when project mcp json contains ijproxy entry without hyphen`(@TempDir projectDir: Path) {
    projectDir.resolve(".mcp.json").writeText(
      """{"mcpServers": {"ijproxy": {"command": "/bin/ij-proxy"}}}"""
    )
    assertThat(projectBaseDirHasIjProxyMcpServer(projectDir)).isTrue()
  }

  @Test
  fun `skips promotion when project codex config contains ij-proxy entry`(@TempDir projectDir: Path) {
    projectDir.resolve(".codex").createDirectories()
    projectDir.resolve(".codex").resolve("config.toml").writeText(
      """
      [mcp_servers.ij-proxy]
      command = "/bin/ij-proxy"
      """.trimIndent()
    )
    assertThat(projectBaseDirHasIjProxyMcpServer(projectDir)).isTrue()
  }

  @Test
  fun `does not skip promotion when project mcp json contains only unrelated entries`(@TempDir projectDir: Path) {
    projectDir.resolve(".mcp.json").writeText(
      """{"mcpServers": {"some-other-server": {"command": "/bin/other"}}}"""
    )
    assertThat(projectBaseDirHasIjProxyMcpServer(projectDir)).isFalse()
  }

  @Test
  fun `does not skip promotion when project has no mcp config files`(@TempDir projectDir: Path) {
    assertThat(projectBaseDirHasIjProxyMcpServer(projectDir)).isFalse()
  }

  private class FakeClient(
    scope: McpClientInfo.Scope,
    private val servers: Map<String, ExistingConfig>?,
    private val hasConfigArtifacts: Boolean = true,
  ) : McpClient(McpClientInfo(McpClientInfo.Name.CODEX, scope), Path.of("fake")) {
    override fun hasConfigArtifacts(): Boolean = hasConfigArtifacts

    override fun readMcpServers(): Map<String, ExistingConfig>? = servers
  }
}