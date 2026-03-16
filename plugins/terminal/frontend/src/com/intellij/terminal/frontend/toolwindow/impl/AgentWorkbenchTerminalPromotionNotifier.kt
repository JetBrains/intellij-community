package com.intellij.terminal.frontend.toolwindow.impl

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.impl.InternalUICustomization
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.installAndEnable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.platform.ide.CoreUiCoroutineScopeHolder
import com.intellij.platform.ide.productMode.IdeProductMode
import com.intellij.terminal.frontend.toolwindow.TerminalTabsManagerListener
import com.intellij.terminal.frontend.view.TerminalView
import com.intellij.terminal.frontend.view.impl.TerminalViewImpl
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.IslandsState
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.util.PathUtilRt
import com.intellij.util.asDisposable
import com.intellij.util.execution.ParametersListUtil
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.plugins.terminal.TerminalBundle
import org.jetbrains.plugins.terminal.fus.ReworkedTerminalUsageCollector
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalCommandExecutionListener
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalCommandStartedEvent
import java.awt.BorderLayout
import java.awt.Insets
import java.util.Locale
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Supplier
import javax.swing.JComponent
import com.intellij.ui.EditorNotificationPanel.Status as NotificationStatus

private val AGENT_WORKBENCH_PLUGIN_ID = PluginId.getId("com.intellij.agent.workbench")
private const val AGENT_WORKBENCH_TOOL_WINDOW_ID = "agent.workbench.sessions"
private const val AGENT_WORKBENCH_PROMOTION_ENABLED_REGISTRY_KEY = "terminal.agent.workbench.promotion.enabled"
private const val AGENT_WORKBENCH_PROMOTION_DISMISSED_KEY = "terminal.agent.workbench.promotion.dismissed"

/**
 * Watches Reworked Terminal tool window tabs and shows an Agent Workbench promotion banner
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
 * 4. After the first matching command in a view, the listener starts a background acquisition attempt
 *    outside the synchronous terminal update path. If another banner is already active, the view is
 *    re-armed for later matching commands.
 * 5. If the promotion gets an active-presentation lease, the notifier posts only the banner creation to
 *    the strict UI dispatcher and logs the `promo.shown` FUS event only after the banner is attached.
 * 6. The promotion is eligible only when all the following are true:
 *    - The command matches a supported provider executable (`codex` or `claude`),
 *    - The promotion registry key is enabled,
 *    - The Agent Workbench plugin is not installed,
 *    - The app-level gate has not marked the promotion as dismissed before.
 *
 * Banner behavior:
 * - The banner is inserted above the terminal content in the current [TerminalViewImpl].
 * - The close action permanently dismisses the promotion and removes the banner.
 * - The install action permanently dismisses the promotion, logs `promo.install.clicked`, installs and
 *   enables Agent Workbench, disposes the banner on success, activates the Agent Workbench tool window,
 *   and then logs
 *   `promo.activation.succeeded`.
 *
 * Gate semantics:
 * - The dismissal flag is stored in [PropertiesComponent] and shared across projects and IDE restarts.
 * - While the promotion is not dismissed, at most one banner can be active at a time across the IDE.
 * - If an active banner disappears because its terminal view is disposed, the promotion becomes eligible
 *   again for later matching commands.
 * - A disabled Agent Workbench plugin is treated as present, so the promotion is suppressed.
 */
internal class AgentWorkbenchTerminalPromotionNotifier(private val project: Project) : TerminalTabsManagerListener {
  private val controller = AgentWorkbenchTerminalPromotionController(
    gate = object : AgentWorkbenchTerminalPromotionGate {
      override fun isDismissed(): Boolean {
        return PropertiesComponent.getInstance().isTrueValue(AGENT_WORKBENCH_PROMOTION_DISMISSED_KEY)
      }

      override fun markDismissed() {
        PropertiesComponent.getInstance().setValue(AGENT_WORKBENCH_PROMOTION_DISMISSED_KEY, true)
      }
    },
    presentationTracker = DefaultAgentWorkbenchTerminalPromotionPresentationTracker,
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
      val promotionState = AtomicReference(AgentWorkbenchTerminalPromotionListenerState.ARMED)
      shellIntegration.addCommandExecutionListener(listenerDisposable, object : TerminalCommandExecutionListener {
        override fun commandStarted(event: TerminalCommandStartedEvent) {
          if (!promotionState.compareAndSet(AgentWorkbenchTerminalPromotionListenerState.ARMED,
                                            AgentWorkbenchTerminalPromotionListenerState.ATTEMPT_IN_PROGRESS)) {
            return
          }
          if (!controller.isPromotionAvailable()) {
            finishPromotionListening(promotionState, listenerDisposable)
            return
          }

          val provider = matchAgentWorkbenchTerminalProvider(event.commandBlock.executedCommand)
          if (provider == null) {
            rearmPromotionListening(promotionState)
            return
          }

          view.coroutineScope.launch(Dispatchers.Default + CoroutineName("Agent Workbench terminal promotion")) {
            when (val acquireResult = controller.tryAcquirePromotion()) {
              AgentWorkbenchTerminalPromotionAcquireResult.Unavailable -> {
                finishPromotionListening(promotionState, listenerDisposable)
              }
              AgentWorkbenchTerminalPromotionAcquireResult.AlreadyPresented -> {
                rearmPromotionListening(promotionState)
              }
              is AgentWorkbenchTerminalPromotionAcquireResult.Acquired -> {
                var bannerShown = false
                try {
                  val presentationResult = withContext(Dispatchers.UI + ModalityState.any().asContextElement()) {
                    presentAgentWorkbenchTerminalPromotion(
                      isProjectDisposed = { project.isDisposed },
                      isViewDisposed = { viewDisposable.isDisposed },
                      attachBanner = {
                        showAgentWorkbenchPromotionBanner(
                          project = project,
                          provider = provider,
                          view = view,
                          dismissPromotion = controller::dismissPromotion,
                          onBannerDisposed = acquireResult.lease::release,
                        )
                      },
                      onShown = {
                        ReworkedTerminalUsageCollector.logAgentWorkbenchPromoShown(project, provider.id)
                      },
                      onAborted = acquireResult.lease::release,
                    )
                  }

                  when (presentationResult) {
                    AgentWorkbenchTerminalPromotionPresentationResult.SHOWN -> {
                      bannerShown = true
                      finishPromotionListening(promotionState, listenerDisposable)
                    }
                    AgentWorkbenchTerminalPromotionPresentationResult.ABORTED -> {
                      finishPromotionListening(promotionState, listenerDisposable)
                    }
                    AgentWorkbenchTerminalPromotionPresentationResult.NOT_ATTACHED -> {
                      rearmPromotionListening(promotionState)
                    }
                  }
                }
                catch (e: CancellationException) {
                  if (project.isDisposed || viewDisposable.isDisposed) {
                    finishPromotionListening(promotionState, listenerDisposable)
                  }
                  else {
                    rearmPromotionListening(promotionState)
                  }
                  throw e
                }
                catch (e: Exception) {
                  rearmPromotionListening(promotionState)
                  throw e
                }
                finally {
                  if (!bannerShown) {
                    acquireResult.lease.release()
                  }
                }
              }
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
 * 4. The app-level dismissal gate allows showing it.
 *    The current gate persists a dismissal flag shared across projects and IDE restarts.
 * 5. The app-level presentation tracker grants at most one active banner lease at a time.
 *
 * The listener uses [isPromotionAvailable] as a cheap arming check and [tryAcquirePromotion] when a
 * matching command has already been detected. The dismissal gate and the presentation tracker are
 * consulted only after the cheaper dynamic preconditions encapsulated by [shouldCheckGate] succeed.
 */
@Internal
class AgentWorkbenchTerminalPromotionController(
  private val gate: AgentWorkbenchTerminalPromotionGate,
  private val presentationTracker: AgentWorkbenchTerminalPromotionPresentationTracker,
  private val shouldCheckGate: () -> Boolean,
) {
  fun isPromotionAvailable(): Boolean {
    if (!shouldCheckGate()) {
      return false
    }
    return !gate.isDismissed()
  }

  fun tryAcquirePromotion(): AgentWorkbenchTerminalPromotionAcquireResult {
    if (!shouldCheckGate()) {
      return AgentWorkbenchTerminalPromotionAcquireResult.Unavailable
    }
    if (gate.isDismissed()) {
      return AgentWorkbenchTerminalPromotionAcquireResult.Unavailable
    }

    val lease = presentationTracker.tryAcquirePresentation()
    return if (lease == null) AgentWorkbenchTerminalPromotionAcquireResult.AlreadyPresented
    else AgentWorkbenchTerminalPromotionAcquireResult.Acquired(lease)
  }

  fun dismissPromotion() {
    gate.markDismissed()
  }
}

private enum class AgentWorkbenchTerminalPromotionListenerState {
  ARMED,
  ATTEMPT_IN_PROGRESS,
  FINISHED,
}

private object DefaultAgentWorkbenchTerminalPromotionPresentationTracker : AgentWorkbenchTerminalPromotionPresentationTracker {
  private val activeBanner = AtomicBoolean(false)

  override fun tryAcquirePresentation(): AgentWorkbenchTerminalPromotionPresentationLease? {
    if (!activeBanner.compareAndSet(false, true)) {
      return null
    }

    val released = AtomicBoolean(false)
    return AgentWorkbenchTerminalPromotionPresentationLease {
      if (released.compareAndSet(false, true)) {
        activeBanner.set(false)
      }
    }
  }
}

@Internal
sealed interface AgentWorkbenchTerminalPromotionAcquireResult {
  object Unavailable : AgentWorkbenchTerminalPromotionAcquireResult
  object AlreadyPresented : AgentWorkbenchTerminalPromotionAcquireResult
  class Acquired(val lease: AgentWorkbenchTerminalPromotionPresentationLease) : AgentWorkbenchTerminalPromotionAcquireResult
}

@Internal
enum class AgentWorkbenchTerminalPromotionPresentationResult {
  SHOWN,
  ABORTED,
  NOT_ATTACHED,
}

@Internal
interface AgentWorkbenchTerminalPromotionPresentationTracker {
  fun tryAcquirePresentation(): AgentWorkbenchTerminalPromotionPresentationLease?
}

@Internal
fun interface AgentWorkbenchTerminalPromotionPresentationLease {
  fun release()
}

@Internal
fun presentAgentWorkbenchTerminalPromotion(
  isProjectDisposed: () -> Boolean,
  isViewDisposed: () -> Boolean,
  attachBanner: () -> Boolean,
  onShown: () -> Unit,
  onAborted: () -> Unit,
): AgentWorkbenchTerminalPromotionPresentationResult {
  if (isProjectDisposed() || isViewDisposed()) {
    onAborted()
    return AgentWorkbenchTerminalPromotionPresentationResult.ABORTED
  }
  if (!attachBanner()) {
    onAborted()
    return AgentWorkbenchTerminalPromotionPresentationResult.NOT_ATTACHED
  }

  onShown()
  return AgentWorkbenchTerminalPromotionPresentationResult.SHOWN
}

@Internal
enum class AgentWorkbenchTerminalProvider(
  @JvmField internal val id: String,
) {
  CODEX("codex"),
  CLAUDE("claude");
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
  fun isDismissed(): Boolean

  fun markDismissed()
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
  dismissPromotion: () -> Unit,
  onBannerDisposed: () -> Unit,
): Boolean {
  val terminalViewImpl = view as? TerminalViewImpl ?: return false
  val bannerDisposable = Disposer.newDisposable(view.coroutineScope.asDisposable(), "AgentWorkbenchPromotionBanner")
  Disposer.register(bannerDisposable) {
    onBannerDisposed()
  }
  val activationHandler = AgentWorkbenchTerminalPromotionActivationHandler(
    isProjectDisposed = { project.isDisposed },
    activateToolWindow = { activateAgentWorkbenchToolWindow(project) },
    onActivationSucceeded = {
      ReworkedTerminalUsageCollector.logAgentWorkbenchPromoActivationSucceeded(project, provider.id)
    },
  )
  val banner = createAgentWorkbenchPromotionBanner(
    onInstallClicked = {
      dismissPromotion()
      ReworkedTerminalUsageCollector.logAgentWorkbenchPromoInstallClicked(project, provider.id)
      installAndEnable(project = project, pluginIds = setOf(AGENT_WORKBENCH_PLUGIN_ID), showDialog = true) {
        Disposer.dispose(bannerDisposable)
        activationHandler.activateAndLogSuccess()
      }
    },
    onClose = {
      dismissPromotion()
      Disposer.dispose(bannerDisposable)
    },
  )
  try {
    terminalViewImpl.setTopComponent(banner, bannerDisposable)
  }
  catch (e: Exception) {
    Disposer.dispose(bannerDisposable)
    throw e
  }
  return true
}

@Internal
fun createAgentWorkbenchPromotionBanner(
  onInstallClicked: () -> Unit,
  onClose: () -> Unit,
) : JComponent {
  val installActionText = TerminalBundle.message("agent.workbench.promotion.install.action")
  val banner = EditorNotificationPanel(NotificationStatus.Info).apply {
    text = TerminalBundle.message("agent.workbench.promotion.banner.text")
    createActionLabel(installActionText, Runnable { onInstallClicked() })
    setCloseAction(Runnable { onClose() })
  }
  val wrappedBanner = InternalUICustomization.getInstance()?.configureEditorTopComponent(banner, true) ?: banner
  return NonOpaquePanel(BorderLayout()).apply {
    @Suppress("UseDPIAwareInsets")
    val supplier = Supplier {
      Insets(if (IslandsState.isEnabled()) 8 else 0, 0, 0, 0)
    }
    border = JBUI.Borders.empty(JBInsets.create(supplier, supplier.get()))
    add(wrappedBanner, BorderLayout.CENTER)
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

private fun finishPromotionListening(
  promotionState: AtomicReference<AgentWorkbenchTerminalPromotionListenerState>,
  listenerDisposable: Disposable,
) {
  promotionState.set(AgentWorkbenchTerminalPromotionListenerState.FINISHED)
  Disposer.dispose(listenerDisposable)
}

private fun rearmPromotionListening(
  promotionState: AtomicReference<AgentWorkbenchTerminalPromotionListenerState>,
) {
  promotionState.compareAndSet(
    AgentWorkbenchTerminalPromotionListenerState.ATTEMPT_IN_PROGRESS,
    AgentWorkbenchTerminalPromotionListenerState.ARMED,
  )
}
