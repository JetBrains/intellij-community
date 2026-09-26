// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.sh.terminal.frontend

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
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
import com.intellij.ui.content.Content
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.terminal.TerminalToolWindowFactory
import org.jetbrains.plugins.terminal.startup.TerminalProcessType
import org.jetbrains.plugins.terminal.util.getNow
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalCommandBlock
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalOutputStatus
import org.jetbrains.plugins.terminal.view.shellIntegration.getTypedCommandText
import java.awt.Component

/**
 * The single frontend implementation of "run this shell command in the Terminal tool window".
 *
 * It serves the Markdown code-fence runner through [ShSelectedTerminalRunner] and the Run File action and the Shell Script run
 * configuration through [run], which [ShTerminalRunRequestListener] calls for every [com.intellij.sh.run.ShRunner] request.
 * Only the reworked terminal engine is supported.
 */
internal class ShFrontendTerminalRunner : ShSelectedTerminalRunner {
  override fun run(
    project: Project,
    command: String,
    @NlsContexts.TabTitle title: String,
    runInNewTerminal: Runnable,
  ): Boolean {
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID) ?: return false
    run(toolWindow, command, title, runInNewTerminal)
    return true
  }

  internal fun run(
    toolWindow: ToolWindow,
    command: String,
    @NlsContexts.TabTitle title: String,
    runInNewTerminal: Runnable,
  ) {
    val selectedContent = toolWindow.contentManager.selectedContent
    if (selectedContent != null && runInTerminalContent(toolWindow, selectedContent, command, title)) {
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
    val terminalView = content.getTerminalTab()?.view ?: return false
    terminalView.createSendTextBuilder().shouldExecute().send(command)
    terminalView.title.change {
      if (userDefinedTitle == null || userDefinedTitle == content.getUserData(LAST_AUTOMATIC_TITLE)) {
        userDefinedTitle = title
        markAutomaticTitle(content, title)
      }
    }
    return true
  }

  override fun hasTerminalStartedFrom(project: Project, sourceFileUrl: String): Boolean {
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID) ?: return false
    return hasTerminalStartedFrom(toolWindow.contentManager.contentsRecursively, sourceFileUrl)
  }

  internal fun associateTerminal(content: Content, sourceFileUrl: String) {
    content.putUserData(MARKDOWN_SOURCE_FILE_URL, sourceFileUrl)
  }

  internal fun markAutomaticTitle(content: Content, @NlsContexts.TabTitle title: String) {
    content.putUserData(LAST_AUTOMATIC_TITLE, title)
  }

  internal fun hasTerminalStartedFrom(contents: List<Content>, sourceFileUrl: String): Boolean {
    return contents.any { it.getUserData(MARKDOWN_SOURCE_FILE_URL) == sourceFileUrl }
  }

  override fun showChooser(
    project: Project,
    command: String,
    @NlsContexts.TabTitle title: String,
    component: Component,
    x: Int,
    y: Int,
    runInNewTerminal: Runnable,
  ): Boolean {
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID) ?: return false
    val terminalContents = toolWindow.contentManager.contentsRecursively.filter(::isTerminal)
    val actions = DefaultActionGroup()
    actions.add(DumbAwareAction.create(ShBundle.message("sh.markdown.runner.terminal.new")) {
      runInNewTerminal.run()
    })
    if (terminalContents.isNotEmpty()) {
      actions.addSeparator()
    }
    terminalContents.forEach { content ->
      actions.add(DumbAwareAction.create(content.displayName) {
        runInTerminalContent(toolWindow, content, command, title)
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
  ) {
    createReworkedTab(project, command, workingDirectory, title, requestFocus = true, sourceFileUrl)
  }

  @RequiresEdt(generateAssertion = false)
  private fun createReworkedTab(
    project: Project,
    command: String,
    workingDirectory: String,
    @NlsContexts.TabTitle title: String,
    requestFocus: Boolean,
    sourceFileUrl: String?,
  ) {
    val tab = TerminalToolWindowTabsManager.getInstance(project)
      .createTabBuilder()
      .workingDirectory(workingDirectory)
      .tabName(title)
      .requestFocus(requestFocus)
      // A tab that stays hidden would never start its shell, and the command would wait forever.
      .deferSessionStartUntilUiShown(requestFocus)
      .createTab()
    if (sourceFileUrl != null) associateTerminal(tab.content, sourceFileUrl)
    markAutomaticTitle(tab.content, title)
    tab.view.createSendTextBuilder().shouldExecute().send(command)
  }

  @RequiresEdt(generateAssertion = false)
  private fun findIdleReworkedTab(project: Project, toolWindow: ToolWindow, workingDirectory: String): TerminalToolWindowTab? {
    val selectedTab = toolWindow.contentManager.selectedContent?.getTerminalTab()
    if (selectedTab != null && isIdleIn(selectedTab, workingDirectory)) return selectedTab
    return TerminalToolWindowTabsManager.getInstance(project).tabs.firstOrNull { it !== selectedTab && isIdleIn(it, workingDirectory) }
  }

  /**
   * A shell tab standing at its prompt in [workingDirectory] with nothing typed. Without shell integration the state of the shell
   * is unknown, so such a tab is never reused.
   */
  private fun isIdleIn(tab: TerminalToolWindowTab, workingDirectory: String): Boolean {
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
    toolWindow: ToolWindow,
    content: Content,
    command: String,
    @NlsContexts.TabTitle title: String,
  ): Boolean {
    if (!sendCommand(content, command, title)) return false

    toolWindow.activate(null)
    content.manager?.setSelectedContent(content)
    return true
  }

  private fun isTerminal(content: Content): Boolean {
    return content.getTerminalTab()?.view != null
  }

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

  companion object {
    internal val MARKDOWN_SOURCE_FILE_URL: Key<String> = Key.create("Shell.Markdown.Source.File.Url")
    internal val LAST_AUTOMATIC_TITLE: Key<@NlsContexts.TabTitle String> = Key.create("Shell.Markdown.Last.Automatic.Title")
  }
}
