package com.intellij.mcpserver.impl

import com.intellij.ide.DataManager
import com.intellij.mcpserver.McpServerBundle
import com.intellij.mcpserver.clients.McpClient
import com.intellij.mcpserver.clients.McpClientInfo
import com.intellij.mcpserver.clients.impl.ClaudeCodeClient
import com.intellij.mcpserver.clients.impl.CodexClient
import com.intellij.mcpserver.settings.McpServerSettingsConfigurable
import com.intellij.mcpserver.util.getConsentDialog
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.impl.InternalUICustomization
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.CheckedDisposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsActions
import com.intellij.openapi.util.io.OSAgnosticPathUtil
import com.intellij.terminal.frontend.toolwindow.TerminalTabsManagerListener
import com.intellij.terminal.frontend.view.TerminalView
import com.intellij.terminal.frontend.view.impl.TerminalViewImpl
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.IslandsState
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.util.PathUtilRt
import com.intellij.util.asDisposable
import com.intellij.util.execution.ParametersListUtil
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.terminal.startup.TerminalProcessType
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalCommandExecutionListener
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalCommandFinishedEvent
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalCommandStartedEvent
import java.awt.BorderLayout
import java.nio.file.Paths
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JComponent
import com.intellij.ui.EditorNotificationPanel.Status as NotificationStatus

private val LOG = logger<McpServerTerminalPromotionNotifier>()

internal class McpServerTerminalPromotionNotifier(private val project: Project) : TerminalTabsManagerListener {
  override fun terminalViewCreated(view: TerminalView) {
    if (McpServerTerminalPromotionDismissalState.isDismissed()) {
      return
    }

    view.coroutineScope.launch {
      val startupOptions = view.startupOptionsDeferred.await()
      if (startupOptions.processType == TerminalProcessType.NON_SHELL) {
        val commandLine = startupOptions.shellCommand
        val executable = commandLine.first()
        val provider = matchMcpServerTerminalProvider(executable) ?: return@launch
        withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
          if (!project.isDisposed) {
            showMcpServerTerminalPromotionBanner(
              project = project,
              promotion = resolveTerminalPromotion(project, provider) ?: return@withContext,
              view = view,
              onBannerPrepared = {},
              onBannerDisposed = {},
            )
          }
        }
      }
      else {
        waitForAgentCommandStart(view)
      }
    }
  }

  private suspend fun waitForAgentCommandStart(view: TerminalView) {
    val shellIntegration = view.shellIntegrationDeferred.await()
    val viewDisposable = Disposer.newCheckedDisposable(view.coroutineScope.asDisposable())
    val promotionState = AtomicReference(McpServerTerminalPromotionListenerState.ATTACHED)
    val activeCommandTracker = McpServerTerminalPromotionActiveCommandTracker<Any>()
    shellIntegration.addCommandExecutionListener(viewDisposable, object : TerminalCommandExecutionListener {
      override fun commandStarted(event: TerminalCommandStartedEvent) {
        if (!promotionState.compareAndSet(McpServerTerminalPromotionListenerState.ATTACHED,
                                          McpServerTerminalPromotionListenerState.ATTEMPT_IN_PROGRESS)) {
          return
        }

        if (McpServerTerminalPromotionDismissalState.isDismissed()) {
          detachPromoListener(promotionState, viewDisposable)
          return
        }

        val provider = matchMcpServerTerminalProvider(event.commandBlock.executedCommand)
        if (provider == null) {
          attachPromoListener(promotionState)
          return
        }
        val commandKey = event.commandBlock.id
        activeCommandTracker.trackStartedCommand(commandKey)

        view.coroutineScope.launch(Dispatchers.Default + CoroutineName("MCP server terminal promotion")) {
          try {
            val promotion = resolveTerminalPromotion(project, provider)
            if (promotion == null) {
              activeCommandTracker.clear(commandKey)
              attachPromoListener(promotionState)
              return@launch
            }

            val bannerShown = withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
              !project.isDisposed && !viewDisposable.isDisposed && showMcpServerTerminalPromotionBanner(
                project = project,
                promotion = promotion,
                view = view,
                onBannerPrepared = { bannerDisposable ->
                  if (!activeCommandTracker.registerBanner(commandKey, bannerDisposable)) {
                    Disposer.dispose(bannerDisposable)
                  }
                },
                onBannerDisposed = { wasAttached ->
                  activeCommandTracker.clear(commandKey)
                  if (wasAttached) {
                    if (shouldKeepListen(
                        isDismissed = McpServerTerminalPromotionDismissalState.isDismissed(),
                        isProjectDisposed = project.isDisposed,
                        isViewDisposed = viewDisposable.isDisposed,
                      )) {
                      attachPromoListener(promotionState)
                    }
                    else {
                      detachPromoListener(promotionState, viewDisposable)
                    }
                  }
                },
              )
            }

            if (!bannerShown) {
              activeCommandTracker.clear(commandKey)
              attachPromoListener(promotionState)
            }
          }
          catch (t: Throwable) {
            LOG.error(t)
            activeCommandTracker.clear(commandKey)
            attachPromoListener(promotionState)
          }
        }
      }

      override fun commandFinished(event: TerminalCommandFinishedEvent) {
        val bannerDisposable = activeCommandTracker.markCommandFinished(event.commandBlock.id) ?: return
        view.coroutineScope.launch(Dispatchers.EDT + ModalityState.any().asContextElement()) {
          if (!bannerDisposable.isDisposed) {
            Disposer.dispose(bannerDisposable)
          }
        }
      }
    })
  }
}

internal enum class McpServerTerminalProvider(
  val command: String,
  val displayName: String,
) {
  CODEX("codex", McpClientInfo.Name.CODEX.baseName),
  CLAUDE("claude", McpClientInfo.Name.CLAUDE_CODE.baseName),
}

internal enum class McpServerTerminalPromotionIssue {
  ENABLE_SERVER,
  CONFIGURE_CLIENT,
  RECONFIGURE_CLIENT,
}

internal data class McpServerTerminalPromotionCandidates(
  val projectClient: McpClient?,
  val globalClient: McpClient?,
) {
  fun setupClients(): List<McpClient> = buildList {
    projectClient?.let(::add)
    globalClient?.let(::add)
  }

  fun reconfigureClients(): List<McpClient> {
    return setupClients().filter { client ->
      client.hasPromotionCandidateConfig() && !client.isPortCorrect()
    }
  }

  fun reconfigureClients(expectedPort: Int): List<McpClient> {
    return setupClients().filter { client ->
      client.hasPromotionCandidateConfig(expectedPort) && !client.isPortCorrect(expectedPort)
    }
  }

  fun preferredClient(): McpClient? {
    if (projectClient?.hasPromotionCandidateConfig() == true) return projectClient
    if (globalClient?.hasPromotionCandidateConfig() == true) return globalClient
    if (projectClient?.hasConfigArtifacts() == true) return projectClient
    if (globalClient?.hasConfigArtifacts() == true) return globalClient
    return setupClients().firstOrNull()
  }
}

private data class McpServerTerminalPromotion(
  val provider: McpServerTerminalProvider,
  val issue: McpServerTerminalPromotionIssue,
  val setupClients: List<McpClient>,
  val reconfigureClients: List<McpClient>,
)

internal fun matchMcpServerTerminalProvider(commandLine: String?): McpServerTerminalProvider? {
  if (commandLine.isNullOrBlank()) {
    return null
  }

  val parsed = ParametersListUtil.parse(commandLine)
  if (parsed.isEmpty()) {
    return null
  }

  val executable = parsed.firstOrNull { token -> !isShellEnvironmentAssignment(token) } ?: return null
  return when (normalizeExecutableName(executable)) {
    McpServerTerminalProvider.CODEX.command -> McpServerTerminalProvider.CODEX
    McpServerTerminalProvider.CLAUDE.command -> McpServerTerminalProvider.CLAUDE
    else -> null
  }
}

internal fun determineMcpServerTerminalPromotionIssue(
  isServerRunning: Boolean,
  isClientConfigured: Boolean?,
  hasPromotionCandidateConfig: Boolean,
  isPortCorrect: Boolean,
): McpServerTerminalPromotionIssue? {
  return when {
    hasPromotionCandidateConfig && !isPortCorrect -> McpServerTerminalPromotionIssue.RECONFIGURE_CLIENT
    !isServerRunning -> McpServerTerminalPromotionIssue.ENABLE_SERVER
    isClientConfigured != true -> McpServerTerminalPromotionIssue.CONFIGURE_CLIENT
    else -> null
  }
}

private enum class McpServerTerminalPromotionListenerState {
  ATTACHED,
  ATTEMPT_IN_PROGRESS,
  DETACHED,
}

internal class McpServerTerminalPromotionActiveCommandTracker<T : Any> {
  private val activeCommandKey = AtomicReference<T?>(null)
  private val commandRunning = AtomicBoolean(false)
  private val bannerDisposable = AtomicReference<CheckedDisposable?>(null)

  fun trackStartedCommand(commandKey: T) {
    activeCommandKey.set(commandKey)
    commandRunning.set(true)
    bannerDisposable.set(null)
  }

  fun markCommandFinished(commandKey: T): CheckedDisposable? {
    if (activeCommandKey.get() != commandKey) {
      return null
    }
    commandRunning.set(false)
    return bannerDisposable.get()
  }

  fun registerBanner(commandKey: T, disposable: CheckedDisposable): Boolean {
    if (activeCommandKey.get() != commandKey) {
      return false
    }
    bannerDisposable.set(disposable)
    return commandRunning.get()
  }

  fun clear(commandKey: T) {
    if (activeCommandKey.compareAndSet(commandKey, null)) {
      commandRunning.set(false)
      bannerDisposable.set(null)
    }
  }
}

private fun resolveTerminalPromotion(project: Project, provider: McpServerTerminalProvider): McpServerTerminalPromotion? {
  if (shouldSkipPromotionDueToProjectIjProxy(project)) return null
  val candidates = collectTerminalPromotionCandidates(project, provider)
  val client = candidates.preferredClient() ?: return null
  val issue = determineMcpServerTerminalPromotionIssue(
    isServerRunning = McpServerService.getInstance().isRunning,
    isClientConfigured = client.isConfigured(),
    hasPromotionCandidateConfig = client.hasPromotionCandidateConfig(),
    isPortCorrect = client.isPortCorrect(),
  ) ?: return null
  return McpServerTerminalPromotion(
    provider = provider,
    issue = issue,
    setupClients = candidates.setupClients(),
    reconfigureClients = candidates.reconfigureClients(),
  )
}

private fun collectTerminalPromotionCandidates(
  project: Project,
  provider: McpServerTerminalProvider,
): McpServerTerminalPromotionCandidates {
  return when (provider) {
    McpServerTerminalProvider.CODEX -> McpServerTerminalPromotionCandidates(
      projectClient = project.basePath?.let { basePath ->
        CodexClient(McpClientInfo.Scope.Project(project), Paths.get(basePath, ".codex", "config.toml"))
      },
      globalClient = McpClientDetector.preferredCodexConfigPath()?.let { configPath ->
        CodexClient(McpClientInfo.Scope.Global, configPath)
      },
    )
    McpServerTerminalProvider.CLAUDE -> McpServerTerminalPromotionCandidates(
      projectClient = project.basePath?.let { basePath ->
        ClaudeCodeClient(McpClientInfo.Scope.Project(project), Paths.get(basePath, ".mcp.json"))
      },
      globalClient = ClaudeCodeClient(
        McpClientInfo.Scope.Global,
        Paths.get(OSAgnosticPathUtil.expandUserHome("~/.claude.json"))
      ),
    )
  }
}

private fun showMcpServerTerminalPromotionBanner(
  project: Project,
  promotion: McpServerTerminalPromotion,
  view: TerminalView,
  onBannerPrepared: (CheckedDisposable) -> Unit,
  onBannerDisposed: (wasAttached: Boolean) -> Unit,
): Boolean {
  val terminalView = view as? TerminalViewImpl ?: return false
  val bannerDisposable = Disposer.newCheckedDisposable(view.coroutineScope.asDisposable(), "McpServerTerminalPromotionBanner")
  var bannerAttached = false
  Disposer.register(bannerDisposable) {
    onBannerDisposed(bannerAttached)
  }

  fun configureClients(clientsToConfigure: List<McpClient>) {
    if (clientsToConfigure.isEmpty()) {
      Disposer.dispose(bannerDisposable)
      return
    }

    if (!McpServerService.getInstance().isRunning) {
      if (!getConsentDialog(project)) {
        return
      }
      McpServerService.getInstance().start()
    }

    view.coroutineScope.launch(Dispatchers.Default + CoroutineName("MCP server terminal promotion setup")) {
      val configuredClients = ArrayList<McpClient>(clientsToConfigure.size)

      for (targetClient in clientsToConfigure.distinct()) {
        val shouldConfigureClient = targetClient.isConfigured() != true || !targetClient.isPortCorrect()
        if (!shouldConfigureClient) {
          continue
        }

        try {
          targetClient.autoConfigure()
          configuredClients.add(targetClient)
        }
        catch (e: CancellationException) {
          throw e
        }
        catch (t: Throwable) {
          LOG.error(t)
        }
      }

      withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
        if (configuredClients.isNotEmpty()) {
          showAutoConfiguredNotification(project, configuredClients)
        }
        Disposer.dispose(bannerDisposable)
      }
    }
  }

  val banner = createMcpServerTerminalPromotionBanner(
    promotion = promotion,
    onSetupClicked = { client -> configureClients(listOf(client)) },
    onFixConfigurationClicked = {
      configureClients(promotion.reconfigureClients)
    },
    onSettingsClicked = {
      ShowSettingsUtil.getInstance().showSettingsDialog(project, McpServerSettingsConfigurable::class.java)
    },
    onDoNotShowAgain = {
      McpServerTerminalPromotionDismissalState.dismiss()
      Disposer.dispose(bannerDisposable)
    },
    onClose = {
      Disposer.dispose(bannerDisposable)
    },
  )
  onBannerPrepared(bannerDisposable)
  if (bannerDisposable.isDisposed) {
    return false
  }
  bannerAttached = true
  terminalView.setTopComponent(banner, bannerDisposable)
  return true
}

private fun createMcpServerTerminalPromotionBanner(
  promotion: McpServerTerminalPromotion,
  onSetupClicked: (McpClient) -> Unit,
  onFixConfigurationClicked: () -> Unit,
  onSettingsClicked: () -> Unit,
  onDoNotShowAgain: () -> Unit,
  onClose: () -> Unit,
): JComponent {
  val banner = EditorNotificationPanel(NotificationStatus.Info).apply {
    text = when (promotion.issue) {
      McpServerTerminalPromotionIssue.ENABLE_SERVER ->
        McpServerBundle.message("mcp.server.terminal.promotion.enable.text",
                                McpServerBundle.ideDisplayName(),
                                promotion.provider.displayName)
      McpServerTerminalPromotionIssue.CONFIGURE_CLIENT ->
        McpServerBundle.message("mcp.server.terminal.promotion.configure.text",
                                promotion.provider.displayName,
                                McpServerBundle.ideDisplayName())
      McpServerTerminalPromotionIssue.RECONFIGURE_CLIENT ->
        McpServerBundle.message("mcp.server.terminal.promotion.reconfigure.text",
                                promotion.provider.displayName,
                                McpServerBundle.ideDisplayName())
    }
    when (promotion.issue) {
      McpServerTerminalPromotionIssue.RECONFIGURE_CLIENT -> {
        @Suppress("DialogTitleCapitalization")
        createActionLabel(mcpServerTerminalPromotionFixActionText(), Runnable { onFixConfigurationClicked() })
      }

      McpServerTerminalPromotionIssue.ENABLE_SERVER,
      McpServerTerminalPromotionIssue.CONFIGURE_CLIENT,
        -> {
        var setupActionLabel: HyperlinkLabel? = null
        setupActionLabel = createActionLabel(
          mcpServerTerminalPromotionSetupGroupActionText(),
          Runnable {
            showMcpServerTerminalPromotionSetupPopup(checkNotNull(setupActionLabel), promotion.setupClients, onSetupClicked)
          },
        )
      }
    }
    createActionLabel(McpServerBundle.message("mcp.server.terminal.promotion.settings.action"), Runnable { onSettingsClicked() })
    createActionLabel(McpServerBundle.message("mcp.server.terminal.promotion.dismiss.action"), Runnable { onDoNotShowAgain() })
    setCloseAction(Runnable { onClose() })
  }
  val wrappedBanner = InternalUICustomization.getInstance()?.configureEditorTopComponent(banner, true) ?: banner
  return NonOpaquePanel(BorderLayout()).apply {
    border = JBUI.Borders.emptyTop(if (IslandsState.isEnabled()) 8 else 0)
    add(wrappedBanner, BorderLayout.CENTER)
  }
}

private fun showMcpServerTerminalPromotionSetupPopup(
  anchor: HyperlinkLabel,
  setupClients: List<McpClient>,
  onSetupClicked: (McpClient) -> Unit,
) {
  JBPopupFactory.getInstance()
    .createActionGroupPopup(
      null,
      createMcpServerTerminalPromotionSetupActionGroup(setupClients, onSetupClicked),
      DataManager.getInstance().getDataContext(anchor),
      JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
      false,
      ActionPlaces.getActionGroupPopupPlace("McpServerTerminalPromotionSetup"),
    )
    .showUnderneathOf(anchor)
}

internal fun createMcpServerTerminalPromotionSetupActionGroup(
  setupClients: List<McpClient>,
  onSetupClicked: (McpClient) -> Unit,
): DefaultActionGroup {
  return DefaultActionGroup(
    setupClients.map { client ->
      object : DumbAwareAction(mcpServerTerminalPromotionSetupActionText(client.mcpClientInfo.scope)) {
        override fun actionPerformed(e: AnActionEvent) {
          onSetupClicked(client)
        }
      }
    },
  )
}

internal fun mcpServerTerminalPromotionSetupGroupActionText(): @Nls(capitalization = Nls.Capitalization.Sentence) String {
  return McpServerBundle.message("mcp.server.terminal.promotion.setup.group.action", McpServerBundle.ideDisplayName())
}

internal fun mcpServerTerminalPromotionFixActionText(): @NlsActions.ActionText String {
  return McpServerBundle.message("mcp.server.terminal.promotion.fix.action")
}

internal fun mcpServerTerminalPromotionSetupActionText(scope: McpClientInfo.Scope): @NlsActions.ActionText String {
  val scopeText = when (scope) {
    is McpClientInfo.Scope.Project -> McpServerBundle.message("mcp.server.terminal.promotion.scope.project")
    is McpClientInfo.Scope.Global -> McpServerBundle.message("mcp.server.terminal.promotion.scope.global")
  }
  return McpServerBundle.message("mcp.server.terminal.promotion.setup.action", McpServerBundle.ideDisplayName(), scopeText)
}

private fun showAutoConfiguredNotification(project: Project, clients: Collection<McpClient>) {
  val clientNames = clients.map { it.mcpClientInfo.displayName }.distinct().joinToString(", ")
  NotificationGroupManager.getInstance()
    .getNotificationGroup("MCP Server")
    .createNotification(
      McpServerBundle.message("mcp.client.autoconfigured"),
      McpServerBundle.message("mcp.server.client.restart.info", clientNames),
      NotificationType.INFORMATION,
    )
    .notify(project)
}

private fun normalizeExecutableName(executable: String): String {
  val fileName = PathUtilRt.getFileName(executable).takeIf { !it.isBlank() } ?: executable
  return fileName.lowercase(Locale.ENGLISH).removeSuffix(".exe")
}

private fun isShellEnvironmentAssignment(token: String): Boolean {
  val separatorIndex = token.indexOf('=')
  if (separatorIndex <= 0) {
    return false
  }

  val variableName = token.substring(0, separatorIndex)
  val first = variableName.first()
  return (first == '_' || first.isLetter()) && variableName.all { it == '_' || it.isLetterOrDigit() }
}

private fun detachPromoListener(
  promotionState: AtomicReference<McpServerTerminalPromotionListenerState>,
  listenerDisposable: Disposable,
) {
  promotionState.set(McpServerTerminalPromotionListenerState.DETACHED)
  Disposer.dispose(listenerDisposable)
}

internal fun shouldKeepListen(
  isDismissed: Boolean,
  isProjectDisposed: Boolean,
  isViewDisposed: Boolean,
): Boolean = !isDismissed && !isProjectDisposed && !isViewDisposed

private fun attachPromoListener(
  promotionState: AtomicReference<McpServerTerminalPromotionListenerState>,
) {
  promotionState.compareAndSet(
    McpServerTerminalPromotionListenerState.ATTEMPT_IN_PROGRESS,
    McpServerTerminalPromotionListenerState.ATTACHED,
  )
}