package com.intellij.terminal.frontend.toolwindow.impl

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.diagnostic.rethrowControlFlowException
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTab
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
import org.jetbrains.plugins.terminal.settings.impl.TerminalSessionPersistedTab
import org.jetbrains.plugins.terminal.settings.impl.TerminalTabsStorage
import kotlin.io.path.pathString
import kotlin.time.Duration.Companion.milliseconds

private val LOG = fileLogger()

/**
 * Watches the terminal tab changes in the [contentManager] and updates persisted tabs in [TerminalTabsStorage].
 */
@OptIn(FlowPreview::class)
@RequiresEdt
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

  listenTerminalTabChangeEvents(contentManager, coroutineScope.childScope("listenTerminalTabChangeEvents")) {
    updateRequestsFlow.tryEmit(Unit)
  }
}

@RequiresEdt
private fun persistTerminalTabs(project: Project, contentManager: ContentManager) {
  try {
    val tabs = contentManager.getTerminalTabs().map { computePersistedTab(it) }
    TerminalTabsStorage.getInstance(project).updateStoredTabs(tabs)
  }
  catch (e: Exception) {
    rethrowControlFlowException(e)
    LOG.error("Error while persisting terminal tabs", e)
  }
}

@RequiresEdt
private fun computePersistedTab(tab: TerminalToolWindowTab): TerminalSessionPersistedTab {
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
 * Calls [onChange] when:
 * 1. Terminal tabs are added or removed from the Terminal Tool Window
 * 2. TerminalView title changes
 * 3. TerminalView working directory changes
 */
@RequiresEdt
private fun listenTerminalTabChangeEvents(
  contentManager: ContentManager,
  coroutineScope: CoroutineScope,
  onChange: () -> Unit,
) {
  val contents = mutableListOf<ContentWithListenersScope>()

  fun addTerminalViewListeners(content: Content) {
    val terminalView = content.getTerminalTab()?.view ?: return  // not a terminal tab

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
}

private data class ContentWithListenersScope(
  val content: Content,
  val listenersScope: CoroutineScope,
)
