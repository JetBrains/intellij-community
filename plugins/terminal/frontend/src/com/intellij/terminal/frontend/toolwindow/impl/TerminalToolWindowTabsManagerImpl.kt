package com.intellij.terminal.frontend.toolwindow.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi
import com.intellij.platform.project.projectId
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.frontend.action.TerminalRenameTabAction
import com.intellij.terminal.frontend.fus.TerminalFocusFusService
import com.intellij.terminal.frontend.toolwindow.*
import com.intellij.terminal.frontend.view.TerminalView
import com.intellij.terminal.frontend.view.TerminalViewSessionState
import com.intellij.terminal.frontend.view.impl.TerminalViewImpl
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManager
import com.intellij.util.AwaitCancellationAndInvoke
import com.intellij.util.EventDispatcher
import com.intellij.util.asDisposable
import com.intellij.util.awaitCancellationAndInvoke
import com.intellij.util.ui.initOnShow
import com.jediterm.core.util.TermSize
import fleet.rpc.client.durable
import kotlinx.coroutines.*
import kotlinx.coroutines.future.await
import org.jetbrains.plugins.terminal.*
import org.jetbrains.plugins.terminal.block.reworked.TerminalPortForwardingUiProvider
import org.jetbrains.plugins.terminal.block.reworked.session.FrontendTerminalSession
import org.jetbrains.plugins.terminal.block.reworked.session.TerminalSessionTab
import org.jetbrains.plugins.terminal.block.reworked.session.rpc.TerminalPortForwardingId
import org.jetbrains.plugins.terminal.block.reworked.session.rpc.TerminalSessionId
import org.jetbrains.plugins.terminal.block.reworked.session.rpc.TerminalTabsManagerApi
import org.jetbrains.plugins.terminal.block.reworked.session.toDto
import org.jetbrains.plugins.terminal.block.ui.TerminalUiUtils
import org.jetbrains.plugins.terminal.fus.ReworkedTerminalUsageCollector
import org.jetbrains.plugins.terminal.fus.TerminalOpeningWay
import org.jetbrains.plugins.terminal.fus.TerminalStartupFusInfo
import kotlin.time.Duration.Companion.seconds

internal class TerminalToolWindowTabsManagerImpl(
  private val project: Project,
  private val coroutineScope: CoroutineScope,
) : TerminalToolWindowTabsManager {
  private val mutableTabs: MutableList<TerminalToolWindowTab> = mutableListOf()

  override val tabs: List<TerminalToolWindowTab>
    get() = mutableTabs.toList()

  private val eventDispatcher = EventDispatcher.create(TerminalTabsManagerListener::class.java)

  private var tabsRestoredDeferred: Deferred<Unit> = CompletableDeferred(Unit)

  init {
    project.messageBus.connect(coroutineScope).subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
      override fun toolWindowShown(toolWindow: ToolWindow) {
        if (toolWindow.id == TerminalToolWindowFactory.TOOL_WINDOW_ID) {
          coroutineScope.launch(Dispatchers.UiWithModelAccess) {
            createNewTabIfEmpty(toolWindow)
          }
        }
      }
    })
  }

  override fun createTabBuilder(): TerminalToolWindowTabBuilder {
    return TerminalToolWindowTabBuilderImpl()
  }

  override fun closeTab(tab: TerminalToolWindowTab) {
    val manager = tab.content.manager ?: return
    manager.removeContent(tab.content, true, true, true)
  }

  override fun detachTab(tab: TerminalToolWindowTab): TerminalView {
    TerminalTabCloseListener.executeContentOperationSilently(tab.content) {
      tab.content.putUserData(TAB_DETACHED_KEY, Unit)
      val manager = tab.content.manager ?: error("No content manager for $tab")
      manager.removeContent(tab.content, true)
    }
    return tab.view
  }

  override fun attachTab(view: TerminalView, contentManager: ContentManager?): TerminalToolWindowTab {
    val tab = doCreateTab(view)
    addToTabsList(tab)
    addTabToToolWindow(tab, contentManager, true)
    return tab
  }

  override fun addListener(parentDisposable: Disposable, listener: TerminalTabsManagerListener) {
    eventDispatcher.addListener(listener, parentDisposable)
  }

  private suspend fun createNewTabIfEmpty(toolWindow: ToolWindow) {
    val fusInfo = TerminalStartupFusInfo(TerminalOpeningWay.OPEN_TOOLWINDOW)

    if (toolWindow.isVisible && toolWindow.contentManager.isEmpty) {
      if (tabsRestoredDeferred.isCompleted) {
        createTerminalTab(project, startupFusInfo = fusInfo)
      }
      else {
        // Wait for some time for backend tabs to be restored.
        withTimeoutOrNull(2.seconds) { tabsRestoredDeferred.await() }
        if (toolWindow.isVisible && toolWindow.contentManager.isEmpty) {
          createTerminalTab(project, startupFusInfo = fusInfo)
        }
      }
    }
  }

  private fun createTab(builder: TerminalToolWindowTabBuilderImpl): TerminalToolWindowTab {
    val terminal = createTerminalViewAndStartSession(builder)
    val tab = doCreateTab(terminal)
    addToTabsList(tab)
    if (builder.shouldAddToToolWindow) {
      addTabToToolWindow(tab, builder.contentManager, builder.requestFocus)
    }
    return tab
  }

  private fun doCreateTab(terminal: TerminalView): TerminalToolWindowTab {
    val panel = TerminalToolWindowPanel()
    panel.setContent(terminal.component)
    val content = ContentFactory.getInstance().createContent(panel, null, false)
    content.setPreferredFocusedComponent { terminal.preferredFocusableComponent }
    TerminalTabCloseListenerImpl.install(content, project, parentDisposable = content)

    val tabScope = coroutineScope.childScope("TerminalToolWindowTab")
    updateTabNameOnTitleChange(terminal.title, content, tabScope.childScope("Tab name updating"))

    // Terminate the session if the tab was closed.
    // But if the tab was detached, leave the session alive.
    Disposer.register(content) {
      tabScope.cancel()
      if (content.getUserData(TAB_DETACHED_KEY) == null) {
        terminal.coroutineScope.cancel()
      }
    }

    // Close the tab if the terminal session was terminated.
    tabScope.launch {
      terminal.sessionState.collect { state ->
        if (state == TerminalViewSessionState.Terminated) {
          // Execute in the manager scope, because closing of the tab may dispose the content and cancel the current coroutine.
          coroutineScope.launch(Dispatchers.UiWithModelAccess + ModalityState.any().asContextElement()) {
            if (TerminalOptionsProvider.instance.closeSessionOnLogout) {
              val tab = findTabByContent(content) ?: return@launch
              closeTab(tab)
            }
          }
        }
      }
    }

    return TerminalToolWindowTabImpl(terminal, content)
  }

  private fun addTabToToolWindow(
    tab: TerminalToolWindowTab,
    contentManager: ContentManager?,
    requestFocus: Boolean,
  ) {
    val toolWindow = getToolWindow()

    val manager = contentManager ?: toolWindow.contentManager
    manager.addContent(tab.content)

    val selectTab = {
      manager.setSelectedContent(tab.content)
    }
    if (requestFocus && !toolWindow.isActive) {
      toolWindow.activate(selectTab, false, false)
    }
    else {
      selectTab()
    }

    eventDispatcher.multicaster.tabCreated(tab)
  }

  private fun addToTabsList(tab: TerminalToolWindowTab) {
    mutableTabs.add(tab)
    Disposer.register(tab.content) {
      mutableTabs.remove(tab)
    }
  }

  private fun createTerminalViewAndStartSession(builder: TerminalToolWindowTabBuilderImpl): TerminalView {
    val terminal = createTerminalView(builder.startupFusInfo)
    val tabName = builder.tabName ?: createDefaultTabName(getToolWindow())
    terminal.title.change { defaultTitle = tabName }
    createBackendTabAndStartSession(terminal, builder)
    return terminal
  }

  private fun createTerminalView(fusInfo: TerminalStartupFusInfo?): TerminalViewImpl {
    val scope = coroutineScope.childScope("Terminal")
    return TerminalViewImpl(project, JBTerminalSystemSettingsProvider(), fusInfo, scope)
  }

  @OptIn(AwaitCancellationAndInvoke::class)
  private fun createBackendTabAndStartSession(
    terminal: TerminalViewImpl,
    builder: TerminalToolWindowTabBuilderImpl,
  ) = terminal.coroutineScope.launch(Dispatchers.IO) {
    val backendTabId = builder.backendTabId ?: durable {
      // todo: worth making it idempotent to avoid creating multiple tabs because of network issues
      TerminalTabsManagerApi.getInstance().createNewTerminalTab(project.projectId()).id
    }

    terminal.coroutineScope.awaitCancellationAndInvoke(Dispatchers.IO) {
      // Backend terminal session tab lifecycle is not directly bound to the terminal frontend lifecycle.
      // We need to close the backend session when the terminal is closed explicitly.
      // And don't need it when a user is closing the project leaving the terminal tabs opened: to be able to reconnect back.
      // So we send close event only if the terminal is closed explicitly: backend will close it on its termination.
      // It is not easy to determine whether it is explicit closing or not, so we use the heuristic.
      val isProjectClosing = getToolWindow().contentManager.isDisposed
      if (!isProjectClosing) {
        durable {
          TerminalTabsManagerApi.getInstance().closeTerminalTab(project.projectId(), backendTabId)
        }
      }
    }

    // Ideally, the backend tab should be under the tab scope, but now it has the lifecycle of the terminal scope
    updateBackendTabNameOnTitleChange(
      terminal.title,
      backendTabId,
      project,
      scope = terminal.coroutineScope.childScope("Backend tab name updating")
    )

    scheduleSessionStart(terminal, builder, backendTabId)
  }

  private suspend fun scheduleSessionStart(
    terminal: TerminalViewImpl,
    builder: TerminalToolWindowTabBuilderImpl,
    backendTabId: Int,
  ) {
    if (builder.deferSessionStartUntilUiShown) {
      withContext(Dispatchers.UI + ModalityState.any().asContextElement()) {
        // Non-cancellable because we expect it to be called only once even if the component was hidden immediately.
        terminal.component.initOnShow("Terminal Session start", context = NonCancellable) {
          doScheduleSessionStart(terminal, builder, backendTabId, calculateSizeFromComponent = true)
        }
      }
    }
    else {
      doScheduleSessionStart(terminal, builder, backendTabId, calculateSizeFromComponent = false)
    }
  }

  private fun doScheduleSessionStart(
    terminal: TerminalViewImpl,
    builder: TerminalToolWindowTabBuilderImpl,
    backendTabId: Int,
    calculateSizeFromComponent: Boolean,
  ) = terminal.coroutineScope.launch(Dispatchers.IO) {
    if (builder.sessionId != null) {
      // Session is already started for this tab, reuse it
      connectSessionToTerminal(terminal, builder.sessionId!!, builder.portForwardingId)
    }
    else {
      val options = prepareStartupOptions(terminal, builder, calculateSizeFromComponent)
      val sessionTab = TerminalTabsManagerApi.getInstance().startTerminalSessionForTab(
        project.projectId(),
        backendTabId,
        options.toDto()
      )
      connectSessionToTerminal(terminal, sessionTab.sessionId!!, sessionTab.portForwardingId)
    }
  }

  private suspend fun prepareStartupOptions(
    terminal: TerminalView,
    builder: TerminalToolWindowTabBuilderImpl,
    calculateSizeFromComponent: Boolean,
  ): ShellStartupOptions {
    val baseOptions = ShellStartupOptions.Builder()
      .shellCommand(builder.shellCommand)
      .workingDirectory(builder.workingDirectory)

    return if (calculateSizeFromComponent) {
      withContext(Dispatchers.UI + ModalityState.any().asContextElement()) {
        TerminalUiUtils.getComponentSizeInitializedFuture(terminal.component).await()
        val termSize = terminal.gridSize?.let { TermSize(it.columns, it.rows) }
        baseOptions.initialTermSize(termSize).build()
      }
    }
    else {
      baseOptions.initialTermSize(TermSize(80, 20)).build()
    }
  }

  private suspend fun connectSessionToTerminal(
    terminal: TerminalViewImpl,
    sessionId: TerminalSessionId,
    portForwardingId: TerminalPortForwardingId?,
  ) = withContext(Dispatchers.UI + ModalityState.any().asContextElement()) {
    val session = FrontendTerminalSession(sessionId)
    terminal.connectToSession(session)

    if (portForwardingId != null) {
      val disposable = terminal.coroutineScope.asDisposable()
      val component = TerminalPortForwardingUiProvider.getInstance(project).createComponent(portForwardingId, disposable)
      if (component != null) {
        terminal.setTopComponent(component, disposable)
      }
    }
  }

  private fun getToolWindow(): ToolWindow {
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID)
                     ?: error("No terminal tool window found")
    toolWindow.contentManager // Ensure that tool window content initialized
    return toolWindow
  }

  internal class Initializer : TerminalToolWindowInitializer {
    override fun initialize(toolWindow: ToolWindow) {
      val manager = TerminalToolWindowTabsManager.getInstance(toolWindow.project) as TerminalToolWindowTabsManagerImpl

      if (ExperimentalUI.isNewUI() && TerminalOptionsProvider.instance.terminalEngine == TerminalEngine.REWORKED) {
        scheduleTabsRestoring(manager)
      }

      val toolWindowActions = ActionManager.getInstance().getAction("Terminal.ToolWindowActions") as? ActionGroup
      toolWindow.setAdditionalGearActions(toolWindowActions)
      ToolWindowContentUi.setAllowTabsReordering(toolWindow, true)

      if (toolWindow is ToolWindowEx) {
        installDirectoryDnD(toolWindow, manager.coroutineScope.asDisposable())
        TerminalDockContainer.install(toolWindow.project, toolWindow.decorator)

        toolWindow.setTabActions(ActionManager.getInstance().getAction("TerminalToolwindowActionGroup"))
        toolWindow.setTabDoubleClickActions(listOf(TerminalRenameTabAction()))
      }

      TerminalFocusFusService.ensureInitialized()
    }

    private fun scheduleTabsRestoring(manager: TerminalToolWindowTabsManagerImpl) {
      manager.tabsRestoredDeferred = manager.coroutineScope.async(Dispatchers.IO) {
        val tabs: List<TerminalSessionTab> = durable {
          TerminalTabsManagerApi.getInstance().getTerminalTabs(manager.project.projectId())
        }
        withContext(Dispatchers.UiWithModelAccess + ModalityState.any().asContextElement()) {
          restoreTabs(tabs, manager)
        }
      }
    }

    private fun restoreTabs(tabs: List<TerminalSessionTab>, manager: TerminalToolWindowTabsManagerImpl) {
      for (tab in tabs) {
        val builder = manager.createTabBuilder() as TerminalToolWindowTabBuilderImpl
        with(builder) {
          shellCommand(tab.shellCommand)
          workingDirectory(tab.workingDirectory)
          tabName(tab.name)
          userDefinedName(tab.isUserDefinedName)
          backendTabId(tab.id)
          sessionId(tab.sessionId)
          portForwardingId(tab.portForwardingId)
        }
        builder.createTab()
      }

      ReworkedTerminalUsageCollector.logSessionRestored(manager.project, tabs.size)

      val contentManager = manager.getToolWindow().contentManager
      val firstContent = contentManager.getContent(0)
      if (firstContent != null) {
        contentManager.setSelectedContent(firstContent)
      }
    }
  }

  private inner class TerminalToolWindowTabBuilderImpl : TerminalToolWindowTabBuilder {
    var workingDirectory: String? = null
      private set
    var shellCommand: List<String>? = null
      private set
    var tabName: String? = null
      private set
    var isUserDefinedName: Boolean = false
      private set
    var requestFocus: Boolean = true
      private set
    var deferSessionStartUntilUiShown: Boolean = true
      private set
    var contentManager: ContentManager? = null
      private set
    var shouldAddToToolWindow: Boolean = true
      private set
    var startupFusInfo: TerminalStartupFusInfo? = null
      private set

    var backendTabId: Int? = null
      private set
    var sessionId: TerminalSessionId? = null
      private set
    var portForwardingId: TerminalPortForwardingId? = null
      private set

    override fun workingDirectory(directory: String?): TerminalToolWindowTabBuilder {
      workingDirectory = directory
      return this
    }

    override fun shellCommand(command: List<String>?): TerminalToolWindowTabBuilder {
      shellCommand = command
      return this
    }

    override fun tabName(name: String?): TerminalToolWindowTabBuilder {
      tabName = name
      return this
    }

    fun userDefinedName(isUserDefinedName: Boolean): TerminalToolWindowTabBuilder {
      this.isUserDefinedName = isUserDefinedName
      return this
    }

    override fun requestFocus(requestFocus: Boolean): TerminalToolWindowTabBuilder {
      this.requestFocus = requestFocus
      return this
    }

    override fun deferSessionStartUntilUiShown(defer: Boolean): TerminalToolWindowTabBuilder {
      deferSessionStartUntilUiShown = defer
      return this
    }

    override fun contentManager(manager: ContentManager?): TerminalToolWindowTabBuilder {
      contentManager = manager
      return this
    }

    override fun shouldAddToToolWindow(addToToolWindow: Boolean): TerminalToolWindowTabBuilder {
      shouldAddToToolWindow = addToToolWindow
      return this
    }

    override fun startupFusInfo(startupFusInfo: TerminalStartupFusInfo?): TerminalToolWindowTabBuilder {
      this.startupFusInfo = startupFusInfo
      return this
    }

    fun backendTabId(id: Int?): TerminalToolWindowTabBuilder {
      backendTabId = id
      return this
    }

    fun sessionId(id: TerminalSessionId?): TerminalToolWindowTabBuilder {
      sessionId = id
      return this
    }

    fun portForwardingId(id: TerminalPortForwardingId?): TerminalToolWindowTabBuilder {
      portForwardingId = id
      return this
    }

    override fun createTab(): TerminalToolWindowTab {
      return createTab(this)
    }
  }

  companion object {
    private val TAB_DETACHED_KEY = Key.create<Unit>("TerminalTabsManager.TabWasDetached")
  }
}