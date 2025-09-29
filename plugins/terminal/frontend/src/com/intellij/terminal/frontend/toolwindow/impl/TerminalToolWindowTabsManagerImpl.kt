package com.intellij.terminal.frontend.toolwindow.impl

import com.intellij.frontend.FrontendApplicationInfo
import com.intellij.frontend.FrontendType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.platform.project.projectId
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.TerminalTitle
import com.intellij.terminal.frontend.TerminalTabBuilder
import com.intellij.terminal.frontend.TerminalView
import com.intellij.terminal.frontend.impl.TerminalViewImpl
import com.intellij.terminal.frontend.toolwindow.TerminalTabsManagerListener
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTab
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTabsManager
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManager
import com.intellij.util.AwaitCancellationAndInvoke
import com.intellij.util.EventDispatcher
import com.intellij.util.awaitCancellationAndInvoke
import com.intellij.util.ui.initOnShow
import com.jediterm.core.util.TermSize
import kotlinx.coroutines.*
import kotlinx.coroutines.future.await
import org.jetbrains.plugins.terminal.*
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
          coroutineScope.launch(Dispatchers.UI) {
            createNewTabIfEmpty(toolWindow)
          }
        }
      }
    })
  }

  override fun createTabBuilder(): TerminalTabBuilder {
    return TerminalTabBuilderImpl()
  }

  override fun closeTab(tab: TerminalToolWindowTab) {
    val manager = tab.content.manager ?: return
    manager.removeContent(tab.content, true, true, true)
  }

  override fun detachTab(tab: TerminalToolWindowTab): TerminalView {
    TerminalTabCloseListener.executeContentOperationSilently(tab.content) {
      tab.content.putUserData(TAB_DETACHED_KEY, Unit)
      closeTab(tab)
    }
    return tab.view
  }

  override fun addListener(parentDisposable: Disposable, listener: TerminalTabsManagerListener) {
    eventDispatcher.addListener(listener, parentDisposable)
  }

  private suspend fun createNewTabIfEmpty(toolWindow: ToolWindow) {
    val fusInfo = TerminalStartupFusInfo(TerminalOpeningWay.OPEN_TOOLWINDOW)

    suspend fun doCreateTab() {
      val engine = TerminalOptionsProvider.instance.terminalEngine
      val frontendType = FrontendApplicationInfo.getFrontendType()
      val isCodeWithMe = frontendType is FrontendType.Remote && frontendType.isGuest()
      if (ExperimentalUI.isNewUI()
          && engine == TerminalEngine.REWORKED
          && !isCodeWithMe) {
        // todo: pass fusInfo there as well
        createTabBuilder().createTab()
      }
      else {
        // Otherwise, create the Classic or Gen1 terminal tab using old API.
        TerminalToolWindowManager.getInstance(project).createNewTab(engine, fusInfo, null, null)
      }
    }

    if (toolWindow.isVisible && toolWindow.contentManager.isEmpty) {
      if (tabsRestoredDeferred.isCompleted) {
        doCreateTab()
      }
      else {
        // Wait for some time for backend tabs to be restored.
        withTimeoutOrNull(2.seconds) { tabsRestoredDeferred.await() }
        if (toolWindow.isVisible && toolWindow.contentManager.isEmpty) {
          doCreateTab()
        }
      }
    }
  }

  private suspend fun createTab(builder: TerminalTabBuilderImpl): TerminalToolWindowTab = withContext(Dispatchers.UI) {
    val toolWindow = getToolWindow() // init tool window

    val tab = doCreateTab(builder)
    mutableTabs.add(tab)
    Disposer.register(tab.content) {
      mutableTabs.remove(tab)
    }

    val contentManager = builder.contentManager ?: toolWindow.contentManager
    contentManager.addContent(tab.content)

    val selectTab = {
      contentManager.setSelectedContent(tab.content)
    }
    if (builder.requestFocus && !toolWindow.isActive) {
      toolWindow.activate(selectTab, false, false)
    }
    else {
      selectTab()
    }

    eventDispatcher.multicaster.tabCreated(tab)

    tab
  }

  private suspend fun doCreateTab(builder: TerminalTabBuilderImpl): TerminalToolWindowTab {
    val title = TerminalTitle()
    val tabName = builder.tabName ?: createDefaultTabName(getToolWindow())
    title.change { defaultTitle = tabName }

    val backendTabId = getOrCreateBackendTabId(builder)
    val terminal = createTerminalView(backendTabId)
    // Ideally, the backend tab should be under the tab scope, but now it has the lifecycle of the terminal scope
    updateBackendTabNameOnTitleChange(
      title,
      backendTabId,
      project,
      scope = terminal.coroutineScope.childScope("Backend tab name updating")
    )
    scheduleSessionStart(terminal, builder, backendTabId, terminal.coroutineScope)

    val panel = TerminalToolWindowPanel()
    panel.setContent(terminal.component)
    val content = ContentFactory.getInstance().createContent(panel, null, false)
    content.setPreferredFocusedComponent { terminal.preferredFocusableComponent }

    val tabScope = coroutineScope.childScope("TerminalToolWindowTab")
    updateTabNameOnTitleChange(title, content, tabScope.childScope("Tab name updating"))
    // todo: install TerminalTabCloseListener

    // Terminate the session if the tab was closed.
    // But if the tab was detached, leave the session alive.
    Disposer.register(content) {
      tabScope.cancel()
      if (content.getUserData(TAB_DETACHED_KEY) == null) {
        terminal.coroutineScope.cancel()
      }
    }

    // Close the tab if the terminal session was terminated.
    terminal.addTerminationCallback(content) {
      coroutineScope.launch(Dispatchers.UI + ModalityState.any().asContextElement()) {
        if (TerminalOptionsProvider.instance.closeSessionOnLogout) {
          val tab = mutableTabs.find { it.content == content } ?: return@launch
          closeTab(tab)
        }
      }
    }

    return TerminalToolWindowTabImpl(terminal, title, content)
  }

  private suspend fun getOrCreateBackendTabId(builder: TerminalTabBuilderImpl): Int {
    return builder.backendTabId ?: withContext(Dispatchers.IO) {
      TerminalTabsManagerApi.getInstance().createNewTerminalTab(project.projectId()).id
    }
  }

  @OptIn(AwaitCancellationAndInvoke::class)
  private fun createTerminalView(backendTabId: Int): TerminalViewImpl {
    val scope = coroutineScope.childScope("Terminal")
    val terminal = TerminalViewImpl(project, JBTerminalSystemSettingsProvider(), null, scope)

    scope.awaitCancellationAndInvoke(Dispatchers.IO) {
      // Backend terminal session tab lifecycle is not directly bound to the terminal frontend lifecycle.
      // We need to close the backend session when the terminal is closed explicitly.
      // And don't need it when a user is closing the project leaving the terminal tabs opened: to be able to reconnect back.
      // So we send close event only if the terminal is closed explicitly: backend will close it on its termination.
      // It is not easy to determine whether it is explicit closing or not, so we use the heuristic.
      val isProjectClosing = getToolWindow().contentManager.isDisposed
      if (!isProjectClosing) {
        TerminalTabsManagerApi.getInstance().closeTerminalTab(project.projectId(), backendTabId)
      }
    }

    return terminal
  }

  private fun scheduleSessionStart(
    terminal: TerminalViewImpl,
    builder: TerminalTabBuilderImpl,
    backendTabId: Int,
    coroutineScope: CoroutineScope,
  ) {
    if (builder.deferSessionStartUntilUiShown) {
      // Non-cancellable because we expect it to be called only once even if the component was hidden immediately.
      terminal.component.initOnShow("Terminal Session start", context = NonCancellable) {
        doScheduleSessionStart(terminal, builder, backendTabId, coroutineScope, calculateSizeFromComponent = true)
      }
    }
    else {
      doScheduleSessionStart(terminal, builder, backendTabId, coroutineScope, calculateSizeFromComponent = false)
    }
  }

  private fun doScheduleSessionStart(
    terminal: TerminalViewImpl,
    builder: TerminalTabBuilderImpl,
    backendTabId: Int,
    coroutineScope: CoroutineScope,
    calculateSizeFromComponent: Boolean,
  ) = coroutineScope.launch(Dispatchers.IO) {
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
    builder: TerminalTabBuilderImpl,
    calculateSizeFromComponent: Boolean,
  ): ShellStartupOptions {
    val baseOptions = ShellStartupOptions.Builder()
      .shellCommand(builder.shellCommand)
      .workingDirectory(builder.workingDirectory)

    return if (calculateSizeFromComponent) {
      withContext(Dispatchers.UI) {
        TerminalUiUtils.getComponentSizeInitializedFuture(terminal.component).await()
        baseOptions.initialTermSize(terminal.size).build()
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
  ) = withContext(Dispatchers.UI) {
    val session = FrontendTerminalSession(sessionId)
    terminal.connectToSession(session)

    if (portForwardingId != null) {
      // todo: setup port forwarding
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
      if (ExperimentalUI.isNewUI() && TerminalOptionsProvider.instance.terminalEngine == TerminalEngine.REWORKED) {
        val manager = TerminalToolWindowTabsManager.getInstance(toolWindow.project) as TerminalToolWindowTabsManagerImpl
        scheduleTabsRestoring(manager)
      }
    }

    private fun scheduleTabsRestoring(manager: TerminalToolWindowTabsManagerImpl) {
      manager.tabsRestoredDeferred = manager.coroutineScope.async(Dispatchers.IO) {
        val tabs: List<TerminalSessionTab> = TerminalTabsManagerApi.getInstance().getTerminalTabs(manager.project.projectId())
        withContext(Dispatchers.UI + ModalityState.any().asContextElement()) {
          restoreTabs(tabs, manager)
        }
      }
    }

    private suspend fun restoreTabs(tabs: List<TerminalSessionTab>, manager: TerminalToolWindowTabsManagerImpl) {
      for (tab in tabs) {
        val builder = manager.createTabBuilder() as TerminalTabBuilderImpl
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

  private inner class TerminalTabBuilderImpl : TerminalTabBuilder {
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

    var backendTabId: Int? = null
      private set
    var sessionId: TerminalSessionId? = null
      private set
    var portForwardingId: TerminalPortForwardingId? = null
      private set

    override fun workingDirectory(directory: String?): TerminalTabBuilder {
      workingDirectory = directory
      return this
    }

    override fun shellCommand(command: List<String>?): TerminalTabBuilder {
      shellCommand = command
      return this
    }

    override fun tabName(name: String?): TerminalTabBuilder {
      tabName = name
      return this
    }

    fun userDefinedName(isUserDefinedName: Boolean): TerminalTabBuilder {
      this.isUserDefinedName = isUserDefinedName
      return this
    }

    override fun requestFocus(requestFocus: Boolean): TerminalTabBuilder {
      this.requestFocus = requestFocus
      return this
    }

    override fun deferSessionStartUntilUiShown(defer: Boolean): TerminalTabBuilder {
      deferSessionStartUntilUiShown = defer
      return this
    }

    override fun contentManager(manager: ContentManager?): TerminalTabBuilder {
      contentManager = manager
      return this
    }

    fun backendTabId(id: Int?): TerminalTabBuilder {
      backendTabId = id
      return this
    }

    fun sessionId(id: TerminalSessionId?): TerminalTabBuilder {
      sessionId = id
      return this
    }

    fun portForwardingId(id: TerminalPortForwardingId?): TerminalTabBuilder {
      portForwardingId = id
      return this
    }

    override suspend fun createTab(): TerminalToolWindowTab {
      return createTab(this)
    }
  }

  companion object {
    private val TAB_DETACHED_KEY = Key.create<Unit>("TerminalTabsManager.TabWasDetached")
  }
}