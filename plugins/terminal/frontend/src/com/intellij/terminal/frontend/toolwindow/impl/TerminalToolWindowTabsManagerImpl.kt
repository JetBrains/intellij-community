package com.intellij.terminal.frontend.toolwindow.impl

import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.frontend.action.TerminalAgentsAvailabilityService
import com.intellij.terminal.frontend.action.TerminalRenameTabAction
import com.intellij.terminal.frontend.fus.TerminalFocusFusService
import com.intellij.terminal.frontend.toolwindow.TerminalRequestedProcessOptions
import com.intellij.terminal.frontend.toolwindow.TerminalTabsManagerListener
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTab
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTabBuilder
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTabsManager
import com.intellij.terminal.frontend.toolwindow.getTerminalTab
import com.intellij.terminal.frontend.view.TerminalView
import com.intellij.terminal.frontend.view.TerminalViewSessionState
import com.intellij.terminal.frontend.view.impl.TerminalViewBuilderOptions
import com.intellij.terminal.frontend.view.impl.createTerminalView
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManager
import com.intellij.util.AwaitCancellationAndInvoke
import com.intellij.util.awaitCancellationAndInvoke
import com.intellij.util.cancelOnDispose
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.terminal.TerminalOptionsProvider
import org.jetbrains.plugins.terminal.TerminalTabCloseListener
import org.jetbrains.plugins.terminal.TerminalToolWindowFactory
import org.jetbrains.plugins.terminal.TerminalToolWindowInitializer
import org.jetbrains.plugins.terminal.TerminalToolWindowPanel
import org.jetbrains.plugins.terminal.fus.ReworkedTerminalUsageCollector
import org.jetbrains.plugins.terminal.fus.TerminalStartupFusInfo
import org.jetbrains.plugins.terminal.fus.TerminalTabOpeningWay
import org.jetbrains.plugins.terminal.settings.impl.TerminalSessionPersistedTab
import org.jetbrains.plugins.terminal.settings.impl.TerminalTabsStorage
import org.jetbrains.plugins.terminal.startup.TerminalProcessType
import org.jetbrains.plugins.terminal.util.TerminalTitleUtils.createDefaultTabName
import kotlin.time.Duration.Companion.seconds

@ApiStatus.Internal
class TerminalToolWindowTabsManagerImpl(
  private val project: Project,
  @VisibleForTesting
  val coroutineScope: CoroutineScope,
) : TerminalToolWindowTabsManager {
  override val tabs: List<TerminalToolWindowTab>
    get() = getToolWindow().contentManager.getTerminalTabs()

  private var tabsRestoredDeferred: Deferred<Unit> = CompletableDeferred(Unit)

  init {
    project.messageBus.connect(coroutineScope).subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
      override fun toolWindowShown(toolWindow: ToolWindow) {
        if (toolWindow.id == TerminalToolWindowFactory.TOOL_WINDOW_ID) {
          coroutineScope.launch(Dispatchers.EDT) {
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
    manager.removeContent(/* content = */ tab.content, /* dispose = */ true, /* requestFocus = */ true, /* forcedFocus = */ true)
  }

  override fun detachTab(tab: TerminalToolWindowTab) {
    var wasContentRemoved = false
    TerminalTabCloseListener.executeContentOperationSilently(tab.content) {
      val contentManager = tab.content.manager
      if (contentManager != null) {
        contentManager.removeContent(tab.content, false)
        wasContentRemoved = true
      }
    }
    // Prevent unnecessary tool window initialization if the tab wasn't added to it (shouldAddToToolWindow(false)).
    if (wasContentRemoved) {
      val toolWindow = getToolWindow()
      if (toolWindow.contentManager.isEmpty) {
        toolWindow.hide()
      }
    }

    project.messageBus.syncPublisher(TerminalTabsManagerListener.TOPIC).tabDetached(tab)
  }

  override fun attachTab(
    tab: TerminalToolWindowTab,
    contentManager: ContentManager?,
  ) {
    addTabToToolWindow(tab, contentManager, true)
  }

  @Suppress("OVERRIDE_DEPRECATION")
  override fun addListener(parentDisposable: Disposable, listener: TerminalTabsManagerListener) {
    project.messageBus.connect(parentDisposable).subscribe(TerminalTabsManagerListener.TOPIC, listener)
  }

  private suspend fun createNewTabIfEmpty(toolWindow: ToolWindow) {
    val fusInfo = TerminalStartupFusInfo(TerminalTabOpeningWay.OPEN_TOOLWINDOW)

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
    val tabScope = coroutineScope.childScope("TerminalToolWindowTab")
    val terminal = createTerminalViewAndStartSession(builder, tabScope.childScope("TerminalView"))
    project.messageBus.syncPublisher(TerminalTabsManagerListener.TOPIC).terminalViewCreated(terminal)

    val tab = doCreateTab(
      project = project,
      terminal = terminal,
      closeOnProcessTermination = builder.closeOnProcessTermination,
      processOptions = builder.getRequestedProcessOptions(),
      coroutineScope = tabScope,
    )
    if (builder.shouldAddToToolWindow) {
      addTabToToolWindow(tab, builder.contentManager, builder.requestFocus)
      ReworkedTerminalUsageCollector.logTabOpened(
        project = project,
        openingWay = builder.startupFusInfo?.way,
        tabCount = getToolWindow().contentManager.contentsRecursively.size
      )
    }
    return tab
  }

  @OptIn(AwaitCancellationAndInvoke::class)
  private fun doCreateTab(
    project: Project,
    terminal: TerminalView,
    closeOnProcessTermination: Boolean,
    processOptions: TerminalRequestedProcessOptions,
    coroutineScope: CoroutineScope,
  ): TerminalToolWindowTab {
    val panel = TerminalToolWindowPanel()
    panel.setContent(terminal.component)
    val content = ContentFactory.getInstance().createContent(panel, null, false)
    content.setPreferredFocusedComponent { terminal.preferredFocusableComponent }
    TerminalTabCloseListenerImpl.install(content, project, parentDisposable = content)

    content.displayName = terminal.getTitleText()
    updateTabNameOnTitleChange(terminal, content, coroutineScope.childScope("Tab name updating"))

    // Wire terminal tab scope lifetime to the Content lifetime.
    // So, if Content is disposed, TerminalView and the process will be terminated.
    coroutineScope.coroutineContext.job.cancelOnDispose(content)

    if (closeOnProcessTermination) {
      coroutineScope.launch(Dispatchers.EDT + ModalityState.any().asContextElement()) {
        terminal.sessionState.collect { state ->
          if (state == TerminalViewSessionState.Terminated) {
            val tab = content.getTerminalTab() ?: return@collect
            TerminalToolWindowTabsManager.getInstance(project).closeTab(tab)
          }
        }
      }
    }

    // In case of project closing there can be a race between terminal coroutine scope cancellation
    // and removing the content from the tool window.
    // If the terminal coroutine scope is canceled before the content is removed, the editor may be shown green for a moment.
    // Let's try to hide the tool window tab right on terminal scope cancellation.
    terminal.coroutineScope.awaitCancellationAndInvoke(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      val manager = content.manager ?: return@awaitCancellationAndInvoke
      manager.removeContent(content, true)
    }

    val tab = TerminalToolWindowTabImpl(terminal, content, closeOnProcessTermination, processOptions)
    content.putUserData(TerminalToolWindowTab.KEY, tab)
    return tab
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
      manager.setSelectedContent(tab.content, requestFocus)
    }
    if (requestFocus && !toolWindow.isActive) {
      toolWindow.activate(selectTab, false, false)
    }
    else {
      selectTab()
    }

    project.messageBus.syncPublisher(TerminalTabsManagerListener.TOPIC).tabAdded(tab)
  }

  private fun createTerminalViewAndStartSession(
    builder: TerminalToolWindowTabBuilderImpl,
    coroutineScope: CoroutineScope,
  ): TerminalView {
    val viewOptions = TerminalViewBuilderOptions(
      processOptions = builder.getRequestedProcessOptions(),
      deferSessionStartUntilUiShown = builder.deferSessionStartUntilUiShown,
      sourceNavigationProjectPath = builder.sourceNavigationProjectPath,
      startupFusInfo = builder.startupFusInfo,
    )
    val terminal = createTerminalView(
      project = project,
      options = viewOptions,
      coroutineScope = coroutineScope
    )
    terminal.title.change {
      if (builder.isUserDefinedName) {
        userDefinedTitle = builder.tabName
      }
      else {
        defaultTitle = builder.tabName ?: createDefaultTabName(getToolWindow())
      }
    }

    return terminal
  }

  private fun getToolWindow(): ToolWindow {
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID)
                     ?: error("No terminal tool window found")
    toolWindow.contentManager // Ensure that tool window content initialized
    return toolWindow
  }

  @RequiresEdt
  private fun installTabsPersistence() {
    val toolWindow = getToolWindow()
    installTerminalTabsPersistence(
      project = toolWindow.project,
      contentManager = toolWindow.contentManager,
      coroutineScope = coroutineScope.childScope("TerminalTabsPersistence")
    )
  }

  internal class Initializer : TerminalToolWindowInitializer {
    override fun initialize(toolWindow: ToolWindow) {
      val manager = TerminalToolWindowTabsManager.getInstance(toolWindow.project) as TerminalToolWindowTabsManagerImpl

      if (shouldUseReworkedTerminal() && TrustedProjects.isProjectTrusted(manager.project)) {
        scheduleTabsRestoring(manager)
      }
      else manager.installTabsPersistence()

      TerminalAgentsAvailabilityService.getInstance(toolWindow.project).prewarm()

      val toolWindowActions = ActionManager.getInstance().getAction("Terminal.ToolWindowActions") as? ActionGroup
      toolWindow.setAdditionalGearActions(toolWindowActions)
      toolWindow.setTabsSplittingAllowed(true)
      ToolWindowContentUi.setToolWindowInEditorSupport(toolWindow, TerminalInEditorSupport())

      TerminalFocusFusService.ensureInitialized()

      if (toolWindow is ToolWindowEx) {
        toolWindow.setTitleActions(listOfNotNull(
          ActionManager.getInstance().getAction("Terminal.AiAgents.LaunchSelectedAgent"),
          ActionManager.getInstance().getAction("Terminal.AiAgents.ChevronSelector"),
          ActionManager.getInstance().getAction("Terminal.AiAgents.AgentSelector"),
        ))
        toolWindow.setTabActions(ActionManager.getInstance().getAction("TerminalToolwindowActionGroup"))
        toolWindow.setTabDoubleClickActions(listOf(TerminalRenameTabAction()))

        // Drag-and-drop and docking rely on the tool window decorator (real UI), which is absent in a headless environment.
        // Skip them there so the tool window can still be initialized in tests.
        if (!ApplicationManager.getApplication().isHeadlessEnvironment) {
          TerminalDnDHandler.installHandler(toolWindow, manager.coroutineScope.childScope("Terminal DnD handler"))
          TerminalDockContainer.install(toolWindow.project, toolWindow.decorator)
        }
      }
    }

    private fun scheduleTabsRestoring(manager: TerminalToolWindowTabsManagerImpl) {
      manager.tabsRestoredDeferred = manager.coroutineScope.async {
        val tabs: List<TerminalSessionPersistedTab> = TerminalTabsStorage.getInstance(manager.project).getStoredTabs()
        withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
          restoreTabs(tabs, manager)
          // Install tabs persistence after restoring already stored tabs to not override them accidentally with empty content.
          manager.installTabsPersistence()
        }
      }
    }

    private fun restoreTabs(tabs: List<TerminalSessionPersistedTab>, manager: TerminalToolWindowTabsManagerImpl) {
      for (tab in tabs) {
        val builder = manager.createTabBuilder() as TerminalToolWindowTabBuilderImpl
        with(builder) {
          shellCommand(tab.shellCommand)
          workingDirectory(tab.workingDirectory)
          envVariables(tab.envVariables ?: emptyMap())
          processType(tab.processType ?: TerminalProcessType.SHELL)
          tabName(tab.name)
          userDefinedName(tab.isUserDefinedName)
          requestFocus(false)  // Otherwise it may trigger the tool window showing
          // Pass null as a trigger time because we don't need to track latency in this case.
          startupFusInfo(TerminalStartupFusInfo(TerminalTabOpeningWay.TABS_RESTORE, triggerTime = null))
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
    var envVariables: Map<String, String> = emptyMap()
      private set
    var processType: TerminalProcessType = TerminalProcessType.SHELL
      private set
    @NlsSafe var tabName: String? = null
      private set
    var isUserDefinedName: Boolean = false
      private set
    var requestFocus: Boolean = true
      private set
    var deferSessionStartUntilUiShown: Boolean = true
      private set
    var contentManager: ContentManager? = null
      private set
    var closeOnProcessTermination: Boolean = TerminalOptionsProvider.instance.closeSessionOnLogout
      private set
    var shouldAddToToolWindow: Boolean = true
      private set
    var sourceNavigationProjectPath: String? = null
      private set
    var startupFusInfo: TerminalStartupFusInfo? = null
      private set

    override fun workingDirectory(directory: String?): TerminalToolWindowTabBuilder {
      workingDirectory = directory
      return this
    }

    override fun shellCommand(command: List<String>?): TerminalToolWindowTabBuilder {
      shellCommand = command
      return this
    }

    override fun envVariables(envs: Map<String, String>): TerminalToolWindowTabBuilder {
      envVariables = envs
      return this
    }

    override fun processType(processType: TerminalProcessType): TerminalToolWindowTabBuilder {
      this.processType = processType
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

    override fun closeOnProcessTermination(shouldClose: Boolean): TerminalToolWindowTabBuilder {
      closeOnProcessTermination = shouldClose
      return this
    }

    override fun shouldAddToToolWindow(addToToolWindow: Boolean): TerminalToolWindowTabBuilder {
      shouldAddToToolWindow = addToToolWindow
      return this
    }

    override fun sourceNavigationProjectPath(projectPath: String?): TerminalToolWindowTabBuilder {
      sourceNavigationProjectPath = projectPath
      return this
    }

    override fun startupFusInfo(startupFusInfo: TerminalStartupFusInfo?): TerminalToolWindowTabBuilder {
      this.startupFusInfo = startupFusInfo
      return this
    }

    override fun createTab(): TerminalToolWindowTab {
      return createTab(this)
    }

    fun getRequestedProcessOptions(): TerminalRequestedProcessOptions {
      return TerminalRequestedProcessOptionsImpl(
        shellCommand = shellCommand,
        workingDirectory = workingDirectory,
        envVariables = envVariables,
        processType = processType,
      )
    }
  }
}
