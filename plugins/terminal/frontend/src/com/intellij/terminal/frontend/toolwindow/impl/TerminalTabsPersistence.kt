package com.intellij.terminal.frontend.toolwindow.impl

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.frontend.toolwindow.TerminalTabsManagerListener
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTab
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTabBuilder
import com.intellij.terminal.frontend.toolwindow.getTerminalTab
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManager
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import org.jetbrains.plugins.terminal.fus.TerminalStartupFusInfo
import org.jetbrains.plugins.terminal.fus.TerminalTabOpeningWay
import org.jetbrains.plugins.terminal.settings.impl.TerminalSessionPersistedTab
import org.jetbrains.plugins.terminal.settings.impl.TerminalTabsStorage
import org.jetbrains.plugins.terminal.startup.TerminalProcessType
import kotlin.io.path.pathString
import kotlin.time.Duration.Companion.milliseconds

private val LOG = fileLogger()

/**
 * Watches the terminal tab changes in the [contentManager] and updates persisted tabs in [TerminalTabsStorage].
 */
@OptIn(FlowPreview::class)
@RequiresEdt(generateAssertion = false /* IJPL-115548 */)
internal fun installTerminalTabsPersistence(
  project: Project,
  contentManager: ContentManager,
  coroutineScope: CoroutineScope,
) {
  val updateRequestsFlow = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  coroutineScope.launch(Dispatchers.UI + ModalityState.any().asContextElement() + CoroutineName("persistTerminalTabs")) {
    persistTerminalTabs(project, contentManager)

    updateRequestsFlow
      .sample(300.milliseconds)
      .collect {
        persistTerminalTabs(project, contentManager)
      }
  }

  listenTerminalTabChangeEvents(project, contentManager, coroutineScope.childScope("listenTerminalTabChangeEvents")) {
    updateRequestsFlow.tryEmit(Unit)
  }
}

@RequiresEdt(generateAssertion = false /* IJPL-115548 */)
private fun persistTerminalTabs(project: Project, contentManager: ContentManager) {
  try {
    val tabs = contentManager.contentsRecursively.mapNotNull { it.computePersistedTabOrNull() }
    TerminalTabsStorage.getInstance(project).updateStoredTabs(tabs)
  }
  catch (e: Exception) {
    rethrowControlFlowException(e)
    LOG.error("Error while persisting terminal tabs", e)
  }
}

/**
 * Returns the state to store for the terminal tab or the pending tab (see [getPendingTerminalTab]) in this content.
 * Returns `null` if the content is not a terminal tab, or if the tab opted out of restoring.
 */
@RequiresEdt(generateAssertion = false /* IJPL-115548 */)
private fun Content.computePersistedTabOrNull(): TerminalSessionPersistedTab? {
  val tab = getTerminalTab() ?: return getPendingTerminalTab()
  // A tab that opted out of restoring is left out of the stored state entirely: restoring one means
  // re-running its command, and for a one-shot process that command outlives everything that gave it a
  // meaning. See TerminalToolWindowTabBuilder.restoreOnProjectReopen.
  return if (tab.restoreOnProjectReopen) computePersistedTab(tab) else null
}

@RequiresEdt(generateAssertion = false /* IJPL-115548 */)
internal fun computePersistedTab(tab: TerminalToolWindowTab): TerminalSessionPersistedTab {
  val title = tab.view.title
  val requestedProcessOptions = tab.processOptions
  val processCurDirectory = tab.view.workingDirectoryFlow.value
  // Prefer current directory of the running process
  val workingDirectory = processCurDirectory?.pathString ?: requestedProcessOptions.workingDirectory

  return TerminalSessionPersistedTab(
    name = title.userDefinedTitle ?: title.defaultTitle,
    isUserDefinedName = title.userDefinedTitle != null,
    shellCommand = requestedProcessOptions.shellCommand,
    workingDirectory = workingDirectory,
    envVariables = requestedProcessOptions.envVariables,
    processType = requestedProcessOptions.processType,
  )
}

/**
 * Applies the options of a tab restored from [TerminalTabsStorage]. It is the reverse of [computePersistedTab].
 */
internal fun TerminalToolWindowTabBuilder.applyPersistedTab(tab: TerminalSessionPersistedTab): TerminalToolWindowTabBuilder {
  return shellCommand(tab.shellCommand)
    .workingDirectory(tab.workingDirectory)
    .envVariables(tab.envVariables ?: emptyMap())
    .processType(tab.processType ?: TerminalProcessType.SHELL)
    .tabName(tab.name)
    .userDefinedName(tab.isUserDefinedName)
    .requestFocus(false)  // Otherwise it may trigger the tool window showing
    // Pass null as a trigger time because we don't need to track latency in this case.
    .startupFusInfo(TerminalStartupFusInfo(TerminalTabOpeningWay.TABS_RESTORE, triggerTime = null))
}

/**
 * Calls [onChange] when:
 * 1. Terminal tabs are added or removed from the Terminal Tool Window
 * 2. TerminalView title changes
 * 3. TerminalView working directory changes
 */
@RequiresEdt(generateAssertion = false /* IJPL-115548 */)
private fun listenTerminalTabChangeEvents(
  project: Project,
  contentManager: ContentManager,
  coroutineScope: CoroutineScope,
  onChange: () -> Unit,
) {
  val contents = mutableListOf<ContentWithListenersScope>()

  fun addTerminalViewListeners(content: Content) {
    val terminalView = content.getTerminalTab()?.view ?: return  // not a terminal tab
    if (contents.any { it.content == content }) return  // already listened

    val listenersScope = coroutineScope.childScope(terminalView.toString())
    listenersScope.launch {
      terminalView.titleStateFlow().collect {
        onChange()
      }
    }
    listenersScope.launch {
      terminalView.workingDirectoryFlow.collect {
        onChange()
      }
    }

    contents.add(ContentWithListenersScope(content, listenersScope))
  }

  val listener = object : ContentManagerListener {
    override fun contentAdded(event: ContentManagerEvent) {
      onChange()
      addTerminalViewListeners(event.content)
    }

    override fun contentRemoved(event: ContentManagerEvent) {
      onChange()

      // Cleanup listeners
      val contentToScope = contents.find { it.content == event.content }
      if (contentToScope != null) {
        contentToScope.listenersScope.cancel()
        contents.remove(contentToScope)
      }
    }
  }

  for (content in contentManager.contentsRecursively) {
    addTerminalViewListeners(content)
  }

  contentManager.addRecursiveContentManagerListener(listener)
  coroutineScope.coroutineContext.job.invokeOnCompletion {
    contentManager.removeRecursiveContentManagerListener(listener)
  }

  // A pending tab gets its view without a contentAdded event.
  project.messageBus.connect(coroutineScope).subscribe(TerminalTabsManagerListener.TOPIC, object : TerminalTabsManagerListener {
    override fun tabAdded(tab: TerminalToolWindowTab) {
      addTerminalViewListeners(tab.content)
    }
  })
}

private data class ContentWithListenersScope(
  val content: Content,
  val listenersScope: CoroutineScope,
)
