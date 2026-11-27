// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.sh.terminal.frontend

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.impl.IdeFrameGuests
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.path.EelPathException
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.sh.ShBundle
import com.intellij.sh.run.ShSelectedTerminalRunner
import com.intellij.sh.run.terminal.ShTerminalRunRequest
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTab
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTabsManager
import com.intellij.terminal.frontend.toolwindow.getTerminalTab
import com.intellij.terminal.frontend.view.TerminalView
import com.intellij.ui.content.Content
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.plugins.terminal.ShellStartupOptions
import org.jetbrains.plugins.terminal.TerminalTabCloseListener
import org.jetbrains.plugins.terminal.TerminalToolWindowFactory
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import org.jetbrains.plugins.terminal.runner.LocalOptionsConfigurer
import org.jetbrains.plugins.terminal.runner.LocalTerminalStartCommandBuilder
import org.jetbrains.plugins.terminal.session.guessShellName
import org.jetbrains.plugins.terminal.startup.TerminalProcessType
import org.jetbrains.plugins.terminal.util.getNow
import org.jetbrains.plugins.terminal.util.terminalProjectScope
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalCommandBlock
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalOutputStatus
import org.jetbrains.plugins.terminal.view.shellIntegration.getTypedCommandText
import java.awt.Component
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The single frontend implementation of "run this shell command in the Terminal tool window".
 *
 * It serves the Markdown code-fence runner through [ShSelectedTerminalRunner] and the Run File action and the Shell Script run
 * configuration through [run], which [ShTerminalRunRequestListener] calls for every [com.intellij.sh.run.ShRunner] request.
 * New tabs always use the reworked terminal engine. A command can also go to an open tab of the Classic Terminal, for example
 * the SSH Session tab, but only when no shell is requested.
 */
internal class ShFrontendTerminalRunner : ShSelectedTerminalRunner {
  override fun run(
    project: Project,
    command: String,
    @NlsContexts.TabTitle title: String,
    runInNewTerminal: Runnable,
    shell: String?,
  ): Boolean {
    val toolWindow = getMarkdownToolWindow(project) ?: return false
    run(project, toolWindow, command, title, runInNewTerminal, shell)
    return true
  }

  internal fun run(
    project: Project,
    toolWindow: ToolWindow,
    command: String,
    @NlsContexts.TabTitle title: String,
    runInNewTerminal: Runnable,
    shell: String? = null,
  ) {
    val selectedContent = toolWindow.contentManager.selectedContent
    if (selectedContent != null && runInTerminalContent(project, toolWindow, selectedContent, command, title, shell)) {
      return
    }
    runInNewTerminal.run()
  }

  /**
   * Runs [request]: a shell tab that sits idle at its prompt in the requested directory is reused, otherwise a new tab named
   * [ShTerminalRunRequest.title] is created. The request's working directory arrives either in the project environment's
   * spelling, which is mapped to the nio path the terminal starts in, or already as a nio path the terminal routes on its own.
   */
  suspend fun run(project: Project, request: ShTerminalRunRequest) {
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID) ?: return
    val workingDirectory = toTerminalWorkingDirectory(project, request.workingDirectory)
    withContext(Dispatchers.EDT) {
      val idleTab = findIdleReworkedTab(project, toolWindow, workingDirectory)
      if (idleTab != null && sendCommand(idleTab.content, request.command, request.title)) {
        showContent(toolWindow, idleTab.content, request.activateToolWindow)
      }
      else {
        createReworkedTab(project, request.command, workingDirectory, request.title, request.activateToolWindow, sourceFileUrl = null)
      }
    }
  }

  internal fun sendCommand(
    content: Content,
    command: String,
    @NlsContexts.TabTitle title: String,
  ): Boolean {
    val terminalView = content.getTerminalTab()?.view
    if (terminalView == null) {
      val classicWidget = TerminalToolWindowManager.findWidgetByContent(content) ?: return false
      classicWidget.sendCommandToExecute(command)
      return true
    }
    enqueueCommand(content, terminalView, command)
    terminalView.title.change {
      if (userDefinedTitle == null || userDefinedTitle == content.getUserData(LAST_AUTOMATIC_TITLE)) {
        userDefinedTitle = title
        markAutomaticTitle(content, title)
      }
    }
    return true
  }

  override fun hasTerminalStartedFrom(project: Project, sourceFileUrl: String): Boolean {
    val toolWindow = getMarkdownToolWindow(project) ?: return false
    return toolWindow.contentManager.contentsRecursively.any {
      it.getUserData(MARKDOWN_SOURCE_FILE_URL) == sourceFileUrl && isMarkdownTerminal(project, toolWindow, it, shell = null)
    }
  }

  internal fun associateTerminal(content: Content, sourceFileUrl: String) {
    content.putUserData(MARKDOWN_SOURCE_FILE_URL, sourceFileUrl)
  }

  internal fun markAutomaticTitle(content: Content, @NlsContexts.TabTitle title: String) {
    content.putUserData(LAST_AUTOMATIC_TITLE, title)
  }

  override fun showChooser(
    project: Project,
    command: String,
    @NlsContexts.TabTitle title: String,
    component: Component,
    x: Int,
    y: Int,
    runInNewTerminal: Runnable,
    shell: String?,
  ): Boolean {
    val toolWindow = getMarkdownToolWindow(project) ?: return false
    val terminalContents = toolWindow.contentManager.contentsRecursively.filter { isMarkdownTerminal(project, toolWindow, it, shell) }
    val actions = DefaultActionGroup()
    actions.add(DumbAwareAction.create(ShBundle.message("sh.markdown.runner.terminal.new")) {
      runInNewTerminal.run()
    })
    if (terminalContents.isNotEmpty()) {
      actions.addSeparator()
    }
    terminalContents.forEach { content ->
      actions.add(DumbAwareAction.create(content.displayName) {
        runInTerminalContent(project, toolWindow, content, command, title, shell)
      })
    }
    ActionManager.getInstance()
      .createActionPopupMenu(ActionPlaces.EDITOR_GUTTER_POPUP, actions)
      .component
      .show(component, x, y)
    return true
  }

  override fun createNewTerminal(
    project: Project,
    command: String,
    workingDirectory: String,
    @NlsContexts.TabTitle title: String,
    sourceFileUrl: String?,
    shell: String?,
  ) {
    if (shell == null) {
      terminalProjectScope(project).launch(start = CoroutineStart.UNDISPATCHED) {
        createMarkdownTab(project, command, workingDirectory, title, sourceFileUrl)
      }
      return
    }
    terminalProjectScope(project).launch(Dispatchers.IO) {
      val options = ShellStartupOptions.Builder().workingDirectory(workingDirectory).build()
      val context = LocalOptionsConfigurer.configureStartupOptions(options, project).toExecOptions()
      val shellCommand = if (context.execCommand.shellName.value == shell) context.execCommand.command
                         else LocalTerminalStartCommandBuilder.convertShellPathToCommand(shell, context.eelDescriptor)
      if (!project.isDisposed) {
        createMarkdownTab(project, command, context.workingDirectory.asNioPath().toString(), title, sourceFileUrl, shellCommand)
      }
    }
  }

  private suspend fun createMarkdownTab(
    project: Project,
    command: String,
    workingDirectory: String,
    @NlsContexts.TabTitle title: String,
    sourceFileUrl: String?,
    shellCommand: List<String>? = null,
  ) = withContext(Dispatchers.EDT) {
    val frameOwner = IdeFrameGuests.frameOwnerOf(project)
    val addToToolWindow = frameOwner === project
    val tab = createReworkedTab(project, command, workingDirectory, title, true, sourceFileUrl, shellCommand, addToToolWindow)
    if (!addToToolWindow) {
      var attached = false
      try {
        TerminalToolWindowTabsManager.getInstance(frameOwner).attachTab(tab)
        attached = true
      }
      finally {
        if (!attached) {
          TerminalTabCloseListener.executeContentOperationSilently(tab.content) {
            TerminalToolWindowTabsManager.getInstance(project).closeTab(tab)
          }
        }
      }
    }
  }

  private suspend fun createReworkedTab(
    project: Project,
    command: String,
    workingDirectory: String,
    @NlsContexts.TabTitle title: String,
    requestFocus: Boolean,
    sourceFileUrl: String?,
    shellCommand: List<String>? = null,
    addToToolWindow: Boolean = true,
  ): TerminalToolWindowTab = withContext(Dispatchers.EDT) {
    val tab = TerminalToolWindowTabsManager.getInstance(project)
      .createTabBuilder()
      .workingDirectory(workingDirectory)
      .shellCommand(shellCommand)
      .tabName(title)
      .requestFocus(requestFocus)
      .shouldAddToToolWindow(addToToolWindow)
      .restoreOnProjectReopen(addToToolWindow)
      // A tab that stays hidden would never start its shell, and the command would wait forever.
      .deferSessionStartUntilUiShown(requestFocus)
      .createTabAsync()
    tab.content.putUserData(TERMINAL_ORIGIN, TerminalOrigin(project, project.getEelDescriptor()))
    if (sourceFileUrl != null) associateTerminal(tab.content, sourceFileUrl)
    markAutomaticTitle(tab.content, title)
    enqueueCommand(tab.content, tab.view, command)
    tab
  }

  private fun enqueueCommand(content: Content, terminalView: TerminalView, command: String) {
    val sendMutex = content.getUserData(COMMAND_SEND_MUTEX) ?: Mutex().also { content.putUserData(COMMAND_SEND_MUTEX, it) }
    // Start immediately so commands reach the mutex in submission order
    terminalView.coroutineScope.launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
      sendMutex.withLock {
        val builder = terminalView.createSendTextBuilder().shouldExecute()
        val isMultiline = '\n' in command
        if (isMultiline && awaitBracketedPasteMode(terminalView)) {
          builder.useBracketedPasteMode()
        }
        else if (isMultiline) {
          LOG.warn("Cannot confirm bracketed paste mode. Sending the command without paste markers.")
        }
        builder.send(command)
      }
    }
  }

  private suspend fun awaitBracketedPasteMode(terminalView: TerminalView): Boolean {
    if (terminalView.bracketedPasteMode.value) return true
    return withTimeoutOrNull(10.seconds) {
      val startupOptions = terminalView.startupOptionsDeferred.await()
      when (startupOptions.processType) {
        TerminalProcessType.SHELL -> Unit
        TerminalProcessType.NON_SHELL -> return@withTimeoutOrNull terminalView.bracketedPasteMode.value
      }
      val shellIntegration = terminalView.shellIntegrationDeferred.getNow()
                             ?: if (startupOptions.envVariables["INTELLIJ_TERMINAL_COMMAND_BLOCKS_REWORKED"] == "1") {
                               terminalView.shellIntegrationDeferred.await()
                             }
                             else return@withTimeoutOrNull terminalView.bracketedPasteMode.value
      shellIntegration.outputStatus.first { it == TerminalOutputStatus.TypingCommand }
      withTimeoutOrNull(100.milliseconds) {
        terminalView.bracketedPasteMode.first { it }
      }
      terminalView.bracketedPasteMode.value
    } ?: false
  }

  @RequiresEdt(generateAssertion = false)
  private fun findIdleReworkedTab(project: Project, toolWindow: ToolWindow, workingDirectory: String): TerminalToolWindowTab? {
    val selectedTab = toolWindow.contentManager.selectedContent?.getTerminalTab()
    if (selectedTab != null && isIdleIn(project, toolWindow, selectedTab, workingDirectory)) return selectedTab
    return TerminalToolWindowTabsManager.getInstance(project).tabs.firstOrNull {
      it !== selectedTab && isIdleIn(project, toolWindow, it, workingDirectory)
    }
  }

  /**
   * A shell tab standing at its prompt in [workingDirectory] with nothing typed. Without shell integration the state of the shell
   * is unknown, so such a tab is never reused.
   */
  private fun isIdleIn(project: Project, toolWindow: ToolWindow, tab: TerminalToolWindowTab, workingDirectory: String): Boolean {
    if (!isProjectTerminal(project, toolWindow, tab.content)) return false
    if (tab.processOptions.processType != TerminalProcessType.SHELL) return false
    val currentDirectory = tab.view.workingDirectoryFlow.value ?: return false
    if (!FileUtil.pathsEqual(workingDirectory, currentDirectory.toString())) return false
    val shellIntegration = tab.view.shellIntegrationDeferred.getNow() ?: return false
    if (shellIntegration.outputStatus.value != TerminalOutputStatus.TypingCommand) return false
    val block = shellIntegration.blocksModel.activeBlock as? TerminalCommandBlock ?: return false
    return block.getTypedCommandText(tab.view.outputModels.regular)?.isEmpty() == true
  }

  @RequiresEdt(generateAssertion = false)
  private fun showContent(toolWindow: ToolWindow, content: Content, activateToolWindow: Boolean) {
    if (activateToolWindow) {
      toolWindow.activate(null)
    }
    content.manager?.setSelectedContent(content)
  }

  private fun runInTerminalContent(
    project: Project,
    toolWindow: ToolWindow,
    content: Content,
    command: String,
    @NlsContexts.TabTitle title: String,
    shell: String?,
  ): Boolean {
    if (!isMarkdownTerminal(project, toolWindow, content, shell) || !sendCommand(content, command, title)) return false

    toolWindow.activate(null)
    content.manager?.setSelectedContent(content)
    return true
  }

  private fun isTerminal(content: Content, shell: String?): Boolean {
    // The shell of a Classic Terminal tab is unknown, so such a tab is accepted only when no shell is requested.
    val tab = content.getTerminalTab() ?: return shell == null && TerminalToolWindowManager.findWidgetByContent(content) != null
    if (shell == null) return true
    val options = tab.view.startupOptionsDeferred.getNow() ?: return false
    return options.processType == TerminalProcessType.SHELL && options.guessShellName().value == shell
  }

  private fun isMarkdownTerminal(project: Project, toolWindow: ToolWindow, content: Content, shell: String?): Boolean =
    isTerminal(content, shell) && isProjectTerminal(project, toolWindow, content)

  private fun isProjectTerminal(project: Project, toolWindow: ToolWindow, content: Content): Boolean {
    val origin = content.getUserData(TERMINAL_ORIGIN)
    if ((origin?.project ?: toolWindow.project) !== project) return false
    if (toolWindow.project === project && IdeFrameGuests.frameGuestsOf(project).isEmpty()) return true
    val descriptor = content.getTerminalTab()?.view?.sessionDeferred?.getNow()?.eelDescriptor ?: origin?.eelDescriptor ?: return false
    return descriptor == project.getEelDescriptor()
  }

  private fun getMarkdownToolWindow(project: Project): ToolWindow? =
    ToolWindowManager.getInstance(IdeFrameGuests.frameOwnerOf(project)).getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID)

  /** A directory spelled by the project's environment becomes the nio path routed to it; anything else is already a nio path. */
  private fun toTerminalWorkingDirectory(project: Project, workingDirectory: String): String {
    return try {
      EelPath.parse(workingDirectory, project.getEelDescriptor()).asNioPath().toString()
    }
    catch (e: EelPathException) {
      workingDirectory
    }
    catch (e: IllegalArgumentException) {
      workingDirectory
    }
  }

  private class TerminalOrigin(val project: Project, val eelDescriptor: EelDescriptor)

  companion object {
    private val LOG = logger<ShFrontendTerminalRunner>()
    private val COMMAND_SEND_MUTEX: Key<Mutex> = Key.create("Shell.Command.Send.Mutex")
    internal val MARKDOWN_SOURCE_FILE_URL: Key<String> = Key.create("Shell.Markdown.Source.File.Url")
    private val TERMINAL_ORIGIN: Key<TerminalOrigin> = Key.create("Shell.Terminal.Origin")
    internal val LAST_AUTOMATIC_TITLE: Key<@NlsContexts.TabTitle String> = Key.create("Shell.Markdown.Last.Automatic.Title")
  }
}
