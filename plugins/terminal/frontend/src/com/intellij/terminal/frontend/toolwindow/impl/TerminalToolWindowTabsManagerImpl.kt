// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.TerminalTitle
import com.intellij.terminal.frontend.action.TerminalEmulatorBadgeAction
import com.intellij.terminal.frontend.action.TerminalRenameTabAction
import com.intellij.terminal.frontend.fus.TerminalFocusFusService
import com.intellij.terminal.frontend.toolwindow.TerminalRequestedProcessOptions
import com.intellij.terminal.frontend.toolwindow.TerminalTabsManagerListener
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTab
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTabBuilder
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTabsManager
import com.intellij.terminal.frontend.toolwindow.getTerminalTab
import com.intellij.terminal.frontend.toolwindow.impl.dnd.TerminalToolWindowDropHandler
import com.intellij.terminal.frontend.view.TerminalView
import com.intellij.terminal.frontend.view.TerminalViewSessionState
import com.intellij.terminal.frontend.view.impl.TerminalViewBuilderOptions
import com.intellij.terminal.frontend.view.impl.createTerminalView
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManager
import com.intellij.util.AwaitCancellationAndInvoke
import com.intellij.util.awaitCancellationAndInvoke
import com.intellij.util.cancelOnDispose
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.terminal.TerminalEmulatorType
import org.jetbrains.plugins.terminal.TerminalOptionsProvider
import org.jetbrains.plugins.terminal.TerminalTabCloseListener
import org.jetbrains.plugins.terminal.TerminalToolWindowFactory
import org.jetbrains.plugins.terminal.TerminalToolWindowInitializer
import org.jetbrains.plugins.terminal.TerminalToolWindowPanel
import org.jetbrains.plugins.terminal.fus.ReworkedTerminalUsageCollector
import org.jetbrains.plugins.terminal.fus.TerminalStartupFusInfo
import org.jetbrains.plugins.terminal.fus.TerminalTabOpeningWay
import org.jetbrains.plugins.terminal.hyperlinks.TerminalSourceNavigationProjectResolver
import org.jetbrains.plugins.terminal.settings.impl.TerminalSessionPersistedTab
import org.jetbrains.plugins.terminal.settings.impl.TerminalTabsStorage
import org.jetbrains.plugins.terminal.startup.TerminalProcessType
import org.jetbrains.plugins.terminal.util.TerminalTitleUtils.buildSettingsAwareFullTitle
import org.jetbrains.plugins.terminal.util.TerminalTitleUtils.buildSettingsAwareTitle
import org.jetbrains.plugins.terminal.util.TerminalTitleUtils.createDefaultTabName

@ApiStatus.Internal
class TerminalToolWindowTabsManagerImpl(
  private val project: Project,
  @VisibleForTesting
  val coroutineScope: CoroutineScope,
) : TerminalToolWindowTabsManager {
  override val tabs: List<TerminalToolWindowTab>
    get() = getToolWindow().contentManager.getTerminalTabs()

  /**
   * Whether the stored tabs are still to be restored. They are restored when the tool window is shown for the first time.
   * Accessed only on EDT.
   */
  private var isTabsRestorePending: Boolean = false

  init {
    project.messageBus.connect(coroutineScope).subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
      override fun toolWindowShown(toolWindow: ToolWindow) {
        if (toolWindow.id == TerminalToolWindowFactory.TOOL_WINDOW_ID) {
          onToolWindowShown(toolWindow)
        }
      }
    })
  }

  override fun createTabBuilder(): TerminalToolWindowTabBuilder {
    return TerminalToolWindowTabBuilderImpl()
  }

  override fun closeTab(tab: TerminalToolWindowTab) {
    val manager = tab.content.manager
    if (manager != null) {
      manager.removeContent(/* content = */ tab.content, /* dispose = */ true, /* requestFocus = */ true, /* forcedFocus = */ true)
    } else {
      tab.content.release()
    }
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

  @VisibleForTesting
  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  internal fun onToolWindowShown(toolWindow: ToolWindow) {
    if (isTabsRestorePending) {
      isTabsRestorePending = false
      restoreTabs(toolWindow)
      // Install tabs persistence after restoring already stored tabs to not override them accidentally with empty content.
      installTabsPersistence()
    }

    if (toolWindow.isVisible && toolWindow.contentManager.isEmpty) {
      createTerminalTab(project, startupFusInfo = TerminalStartupFusInfo(TerminalTabOpeningWay.OPEN_TOOLWINDOW))
    }
  }

  /**
   * Adds the stored tabs as pending tabs (see [getPendingTerminalTab]) before the tabs that are already in the tool window.
   * The tab that becomes selected is built right away by [installPendingTabsBuilding].
   */
  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  private fun restoreTabs(toolWindow: ToolWindow) {
    val contentManager = toolWindow.contentManager
    installPendingTabsBuilding(project, contentManager, coroutineScope.childScope("TerminalPendingTabsBuilding"))

    // Keep the selection if a tab was added to the tool window before the restore.
    val wasEmpty = contentManager.isEmpty
    val tabs = TerminalTabsStorage.getInstance(project).getStoredTabs()
    for ((index, tab) in tabs.withIndex()) {
      addPendingTab(tab, index)
    }

    logInBackground { ReworkedTerminalUsageCollector.logSessionRestored(project, tabs.size) }

    if (wasEmpty) {
      contentManager.contents.firstOrNull()?.let {
        contentManager.setSelectedContent(it)
      }
    }
  }

  private fun createTab(builder: TerminalToolWindowTabBuilderImpl): TerminalToolWindowTab {
    val tabScope = coroutineScope.childScope("TerminalToolWindowTab")
    val terminal = createTerminalViewAndStartSession(builder, tabScope.childScope("TerminalView"))
    project.messageBus.syncPublisher(TerminalTabsManagerListener.TOPIC).terminalViewCreated(terminal)

    val pendingContent = builder.pendingContent
    val tab = doCreateTab(
      project = project,
      terminal = terminal,
      content = pendingContent ?: createTabContent(),
      closeOnProcessTermination = builder.closeOnProcessTermination,
      restoreOnProjectReopen = builder.restoreOnProjectReopen,
      processOptions = builder.getRequestedProcessOptions(),
      coroutineScope = tabScope,
    )
    if (pendingContent != null) {
      project.messageBus.syncPublisher(TerminalTabsManagerListener.TOPIC).tabAdded(tab)
    }
    else if (builder.shouldAddToToolWindow) {
      addTabToToolWindow(tab, builder.contentManager, builder.requestFocus)
      val openingWay = builder.startupFusInfo?.way
      val tabCount = getToolWindow().contentManager.contentsRecursively.size
      logInBackground { ReworkedTerminalUsageCollector.logTabOpened(project, openingWay, tabCount) }
    }
    return tab
  }

  private fun createTabContent(): Content {
    return ContentFactory.getInstance().createContent(TerminalToolWindowPanel(), null, false)
  }

  @OptIn(AwaitCancellationAndInvoke::class)
  private fun doCreateTab(
    project: Project,
    terminal: TerminalView,
    content: Content,
    closeOnProcessTermination: Boolean,
    restoreOnProjectReopen: Boolean,
    processOptions: TerminalRequestedProcessOptions,
    coroutineScope: CoroutineScope,
  ): TerminalToolWindowTab {
    (content.component as TerminalToolWindowPanel).setContent(terminal.component)
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

    val tab = TerminalToolWindowTabImpl(terminal, content, closeOnProcessTermination, restoreOnProjectReopen, processOptions)
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
      sourceNavigationProjectResolver = builder.sourceNavigationProjectResolver,
      startupFusInfo = builder.startupFusInfo,
    )
    val terminal = createTerminalView(
      project = project,
      options = viewOptions,
      coroutineScope = coroutineScope
    )
    terminal.title.applyTabName(builder.tabName, builder.isUserDefinedName)

    return terminal
  }

  private fun TerminalTitle.applyTabName(@NlsSafe tabName: String?, isUserDefinedName: Boolean) {
    change {
      if (isUserDefinedName) {
        userDefinedTitle = tabName
      }
      else {
        defaultTitle = tabName ?: createDefaultTabName(project, getToolWindow())
      }
    }
  }

  /**
   * Adds [storedTab] to the tool window at [index] as a pending tab (see [getPendingTerminalTab]) without selecting it.
   * The tab label and tooltip are the same as the built tab will have.
   */
  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  private fun addPendingTab(storedTab: TerminalSessionPersistedTab, index: Int) {
    val title = TerminalTitle()
    title.applyTabName(storedTab.name, storedTab.isUserDefinedName)

    val content = createTabContent()
    content.displayName = title.buildSettingsAwareTitle()
    content.description = StringUtil.escapeXmlEntities(title.buildSettingsAwareFullTitle())
    // Keep the resolved default name, so the built tab gets the same name.
    content.setPendingTerminalTab(storedTab.copy(name = title.userDefinedTitle ?: title.defaultTitle))

    val contentManager = getToolWindow().contentManager
    contentManager.addContent(content, index)
    val tabCount = contentManager.contentsRecursively.size
    logInBackground { ReworkedTerminalUsageCollector.logTabOpened(project, TerminalTabOpeningWay.TABS_RESTORE, tabCount) }
  }

  /** The first FUS call initializes the collector, which is slow. Read the values on the EDT and log in the background. */
  private fun logInBackground(log: () -> Unit) {
    coroutineScope.launch(Dispatchers.Default) { log() }
  }

  private fun getToolWindow(): ToolWindow {
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID)
                     ?: error("No terminal tool window found")
    toolWindow.contentManager // Ensure that tool window content initialized
    return toolWindow
  }

  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
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
        // Any plugin can initialize the tool window without showing it, so the tabs are restored on the first show.
        manager.isTabsRestorePending = true
      }
      else manager.installTabsPersistence()

      val toolWindowActions = ActionManager.getInstance().getAction("Terminal.ToolWindowActions") as? ActionGroup
      toolWindow.setAdditionalGearActions(toolWindowActions)
      toolWindow.setTitleActions(listOf(TerminalEmulatorBadgeAction()))
      toolWindow.setTabsSplittingAllowed(true)
      ToolWindowContentUi.setToolWindowInEditorSupport(toolWindow, TerminalInEditorSupport())

      TerminalFocusFusService.ensureInitialized()

      if (toolWindow is ToolWindowEx) {
        toolWindow.setTabActions(ActionManager.getInstance().getAction("TerminalToolwindowActionGroup"))
        toolWindow.setTabDoubleClickActions(listOf(TerminalRenameTabAction()))

        // Drag-and-drop and docking rely on the tool window decorator (real UI), which is absent in a headless environment.
        // Skip them there so the tool window can still be initialized in tests.
        if (!ApplicationManager.getApplication().isHeadlessEnvironment) {
          TerminalToolWindowDropHandler.install(toolWindow, manager.coroutineScope.childScope("Terminal DnD handler"))
          TerminalDockContainer.install(toolWindow.project, toolWindow.decorator)
        }
      }
    }
  }

  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  internal fun createDetachedTab(
    row: TerminalSessionPersistedTab,
  ): TerminalToolWindowTab {
    return createTabBuilder()
      .applyPersistedTab(row)
      .shouldAddToToolWindow(false)
      .createTab()
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
    var emulatorType: TerminalEmulatorType? = null
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
    var restoreOnProjectReopen: Boolean = true
      private set
    var shouldAddToToolWindow: Boolean = true
      private set
    var sourceNavigationProjectResolver: TerminalSourceNavigationProjectResolver? = null
      private set
    var startupFusInfo: TerminalStartupFusInfo? = null
      private set
    var pendingContent: Content? = null
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

    override fun emulatorType(emulatorType: TerminalEmulatorType?): TerminalToolWindowTabBuilder {
      this.emulatorType = emulatorType
      return this
    }

    override fun tabName(name: String?): TerminalToolWindowTabBuilder {
      tabName = name
      return this
    }

    override fun userDefinedName(isUserDefinedName: Boolean): TerminalToolWindowTabBuilder {
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

    override fun restoreOnProjectReopen(restore: Boolean): TerminalToolWindowTabBuilder {
      restoreOnProjectReopen = restore
      return this
    }

    override fun shouldAddToToolWindow(addToToolWindow: Boolean): TerminalToolWindowTabBuilder {
      shouldAddToToolWindow = addToToolWindow
      return this
    }

    override fun sourceNavigationProjectResolver(resolver: TerminalSourceNavigationProjectResolver?): TerminalToolWindowTabBuilder {
      sourceNavigationProjectResolver = resolver
      return this
    }

    override fun startupFusInfo(startupFusInfo: TerminalStartupFusInfo?): TerminalToolWindowTabBuilder {
      this.startupFusInfo = startupFusInfo
      return this
    }

    override fun pendingContent(content: Content): TerminalToolWindowTabBuilder {
      pendingContent = content
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
        emulatorType = emulatorType,
      )
    }
  }
}
