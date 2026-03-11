package com.intellij.terminal.frontend.toolwindow.impl

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.installAndEnable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.platform.ide.CoreUiCoroutineScopeHolder
import com.intellij.platform.ide.productMode.IdeProductMode
import com.intellij.terminal.frontend.toolwindow.TerminalTabsManagerListener
import com.intellij.terminal.frontend.view.TerminalView
import com.intellij.terminal.frontend.view.impl.TerminalViewImpl
import com.intellij.ui.ClientProperty
import com.intellij.ui.EditorNotificationPanel
import com.intellij.util.PathUtilRt
import com.intellij.util.asDisposable
import com.intellij.util.execution.ParametersListUtil
import com.intellij.util.ui.JBUI
import com.intellij.xml.util.XmlStringUtil
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.plugins.terminal.TerminalBundle
import org.jetbrains.plugins.terminal.fus.ReworkedTerminalUsageCollector
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalCommandExecutionListener
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalCommandStartedEvent
import java.util.Locale
import java.util.concurrent.CancellationException
import javax.swing.SwingConstants
import com.intellij.ui.EditorNotificationPanel.Status as NotificationStatus

private val AGENT_WORKBENCH_PLUGIN_ID = PluginId.getId("com.intellij.agent.workbench")
private const val AGENT_WORKBENCH_TOOL_WINDOW_ID = "agent.workbench.sessions"
private const val AGENT_WORKBENCH_PROMOTION_ENABLED_REGISTRY_KEY = "terminal.agent.workbench.promotion.enabled"
private const val AGENT_WORKBENCH_PROMOTION_SHOWN_KEY = "terminal.agent.workbench.promotion.shown"

/**
 * Watches Reworked Terminal tool window tabs and shows a one-time Agent Workbench promotion banner
 * when the user starts working with supported AI CLIs directly in the terminal.
 *
 * Feature flow:
 * 1. The notifier is attached as a [TerminalTabsManagerListener], so it only observes terminal views
 *    created for the Terminal tool window.
 * 2. For each created view, it waits until shell integration is initialized and then subscribes to
 *    [TerminalCommandExecutionListener.commandStarted]. If shell integration is unavailable for the
 *    session, the promotion logic is never armed for that view.
 * 3. The notifier arms each view only while the promotion is still potentially relevant.
 *    In the synchronous `commandStarted` callback it performs only a cheap armed-check and supported
 *    executable match on `executedCommand`.
 * 4. After the first matching command in a view, the listener is disarmed, and the remaining
 *    plugin/gate checks are deferred to a background coroutine, outside the synchronous terminal
 *    update path.
 * 5. If the promotion is accepted, the notifier logs the `promo.shown` FUS event and posts only the
 *    banner creation to the strict UI dispatcher.
 * 6. The promotion is eligible only when all the following are true:
 *    - The command matches a supported provider executable (`codex` or `claude`),
 *    - The promotion registry key is enabled,
 *    - The Agent Workbench plugin is not installed,
 *    - The app-level gate has not marked the promotion as shown before.
 *
 * Banner behavior:
 * - The banner is inserted above the terminal content in the current [TerminalViewImpl].
 * - The close action dismisses the banner for the current terminal view only.
 * - The install action logs `promo.install.clicked`, installs and enables Agent Workbench, disposes
 *   the banner on success, activates the Agent Workbench tool window, and then logs
 *   `promo.activation.succeeded`.
 *
 * Gate semantics:
 * - The "shown" flag is stored in [PropertiesComponent] and shared across projects and IDE restarts.
 * - The promotion is therefore shown at most once per IDE configuration, not once per project or
 *   terminal tab.
 * - A disabled Agent Workbench plugin is treated as present, so the promotion is suppressed.
 */
internal class AgentWorkbenchTerminalPromotionNotifier(private val project: Project) : TerminalTabsManagerListener {
  private val controller = AgentWorkbenchTerminalPromotionController(
    gate = object : AgentWorkbenchTerminalPromotionGate {
      override fun isShown(): Boolean {
        return PropertiesComponent.getInstance().isTrueValue(AGENT_WORKBENCH_PROMOTION_SHOWN_KEY)
      }

      override fun tryMarkShown(): Boolean {
        return PropertiesComponent.getInstance().updateValue(AGENT_WORKBENCH_PROMOTION_SHOWN_KEY, true)
      }
    },
    shouldCheckGate = {
      Registry.`is`(AGENT_WORKBENCH_PROMOTION_ENABLED_REGISTRY_KEY, true) &&
      !IdeProductMode.isFrontend &&
      !PluginManagerCore.isPluginInstalled(AGENT_WORKBENCH_PLUGIN_ID)
    },
  )

  override fun terminalViewCreated(view: TerminalView) {
    try {
      if (!controller.isPromotionAvailable()) {
        return
      }
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Exception) {
      thisLogger().error(e)
      return
    }

    view.coroutineScope.launch {
      val shellIntegration = view.shellIntegrationDeferred.await()
      if (!controller.isPromotionAvailable()) {
        return@launch
      }

      val viewDisposable = Disposer.newCheckedDisposable(view.coroutineScope.asDisposable(), "AgentWorkbenchTerminalPromotionView")
      val listenerDisposable = Disposer.newDisposable(viewDisposable, "AgentWorkbenchTerminalPromotionListener")
      var promotionArmed = true
      shellIntegration.addCommandExecutionListener(listenerDisposable, object : TerminalCommandExecutionListener {
        override fun commandStarted(event: TerminalCommandStartedEvent) {
          if (!promotionArmed) {
            return
          }
          if (!controller.isPromotionAvailable()) {
            promotionArmed = false
            return
          }

          val provider = matchAgentWorkbenchTerminalProvider(event.commandBlock.executedCommand) ?: return
          promotionArmed = false
          Disposer.dispose(listenerDisposable)
          view.coroutineScope.launch(Dispatchers.Default + CoroutineName("Agent Workbench terminal promotion")) {
            if (!controller.tryAcquirePromotion()) {
              return@launch
            }

            ReworkedTerminalUsageCollector.logAgentWorkbenchPromoShown(project, provider.id)

            withContext(Dispatchers.UI + ModalityState.any().asContextElement()) {
              if (project.isDisposed || viewDisposable.isDisposed) {
                return@withContext
              }
              showAgentWorkbenchPromotionBanner(project, provider, view)
            }
          }
        }
      })
    }
  }
}

/**
 * Tracks whether the Agent Workbench terminal promotion is still eligible to be shown.
 *
 * The promotion is shown only when all the following are true:
 * 1. The registry key enabling the promotion is turned on.
 * 2. The terminal command already matched one of the supported providers.
 * 3. Agent Workbench is missing from the IDE installation.
 *    If the plugin is installed but disabled, it is treated as present and the promotion is not shown.
 * 4. The app-level promotion gate allows showing it.
 *    The current gate persists a once-ever "shown" flag shared across projects and IDE restarts.
 *
 * The listener uses [isPromotionAvailable] as a cheap arming check and [tryAcquirePromotion] when a
 * matching command has already been detected. The gate is consulted only after the cheaper dynamic
 * preconditions encapsulated by [shouldCheckGate] succeed.
 */
@Internal
class AgentWorkbenchTerminalPromotionController(
  private val gate: AgentWorkbenchTerminalPromotionGate,
  private val shouldCheckGate: () -> Boolean,
) {
  fun isPromotionAvailable(): Boolean {
    if (!shouldCheckGate()) {
      return false
    }
    return !gate.isShown()
  }

  fun tryAcquirePromotion(): Boolean {
    if (!shouldCheckGate()) {
      return false
    }
    return gate.tryMarkShown()
  }
}

@Internal
enum class AgentWorkbenchTerminalProvider(
  @JvmField internal val id: String,
  private val bundleKey: String,
) {
  CODEX("codex", "agent.workbench.promotion.provider.codex"),
  CLAUDE("claude", "agent.workbench.promotion.provider.claude");

  fun displayName(): String = TerminalBundle.message(bundleKey)
}

@Internal
class AgentWorkbenchTerminalPromotionActivationHandler(
  private val isProjectDisposed: () -> Boolean,
  private val activateToolWindow: () -> Boolean,
  private val onActivationSucceeded: () -> Unit,
) {
  fun activateAndLogSuccess() {
    if (isProjectDisposed()) {
      return
    }
    if (!activateToolWindow()) {
      return
    }
    onActivationSucceeded()
  }
}

@Internal
interface AgentWorkbenchTerminalPromotionGate {
  fun isShown(): Boolean

  fun tryMarkShown(): Boolean
}

@Internal
fun matchAgentWorkbenchTerminalProvider(commandLine: String?): AgentWorkbenchTerminalProvider? {
  if (commandLine.isNullOrBlank()) {
    return null
  }

  val parsed = ParametersListUtil.parse(commandLine)
  if (parsed.isEmpty()) {
    return null
  }

  val executable = parsed.firstOrNull { token -> !isShellEnvironmentAssignment(token) } ?: return null
  return when (normalizeExecutableName(executable)) {
    AgentWorkbenchTerminalProvider.CODEX.id -> AgentWorkbenchTerminalProvider.CODEX
    AgentWorkbenchTerminalProvider.CLAUDE.id -> AgentWorkbenchTerminalProvider.CLAUDE
    else -> null
  }
}

private fun showAgentWorkbenchPromotionBanner(
  project: Project,
  provider: AgentWorkbenchTerminalProvider,
  view: TerminalView,
) {
  val terminalViewImpl = view as? TerminalViewImpl ?: return
  val bannerDisposable = Disposer.newDisposable(view.coroutineScope.asDisposable(), "AgentWorkbenchPromotionBanner")
  val activationHandler = AgentWorkbenchTerminalPromotionActivationHandler(
    isProjectDisposed = { project.isDisposed },
    activateToolWindow = { activateAgentWorkbenchToolWindow(project) },
    onActivationSucceeded = {
      ReworkedTerminalUsageCollector.logAgentWorkbenchPromoActivationSucceeded(project, provider.id)
    },
  )
  val banner = AgentWorkbenchTerminalPromotionPanel(
    provider = provider,
    onInstallClicked = {
      ReworkedTerminalUsageCollector.logAgentWorkbenchPromoInstallClicked(project, provider.id)
      installAndEnable(project = project, pluginIds = setOf(AGENT_WORKBENCH_PLUGIN_ID), showDialog = true) {
        Disposer.dispose(bannerDisposable)
        activationHandler.activateAndLogSuccess()
      }
    },
    onClose = {
      Disposer.dispose(bannerDisposable)
    },
  )
  terminalViewImpl.setTopComponent(banner, bannerDisposable)
}

private class AgentWorkbenchTerminalPromotionPanel(
  provider: AgentWorkbenchTerminalProvider,
  onInstallClicked: () -> Unit,
  onClose: () -> Unit,
) : EditorNotificationPanel(NotificationStatus.Info) {
  init {
    @Suppress("DialogTitleCapitalization") // Agent Workbench is a product name.
    val installActionText = TerminalBundle.message("agent.workbench.promotion.install.action")
    text = XmlStringUtil.wrapInHtml(
      StringUtil.escapeXmlEntities(
        TerminalBundle.message("agent.workbench.promotion.banner.text", provider.displayName()),
      ),
    )
    ClientProperty.get(this, FileEditorManager.SEPARATOR_BORDER)?.let { separatorBorder ->
      border = JBUI.Borders.compound(separatorBorder, border)
    }
    myLabel.verticalTextPosition = SwingConstants.TOP
    createActionLabel(installActionText) {
      onInstallClicked()
    }
    setCloseAction {
      onClose()
    }
  }
}

private fun activateAgentWorkbenchToolWindow(project: Project): Boolean {
  project.service<CoreUiCoroutineScopeHolder>().coroutineScope.launch(Dispatchers.EDT) {
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(AGENT_WORKBENCH_TOOL_WINDOW_ID) ?: return@launch
    toolWindow.activate(null)
  }
  return true
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
  if (variableName.first() != '_' && !variableName.first().isLetter()) {
    return false
  }

  return variableName.all { it == '_' || it.isLetterOrDigit() }
}
