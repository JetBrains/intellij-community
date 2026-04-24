// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.action

import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.openapi.wm.ToolWindow
import com.intellij.platform.project.projectId
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTabsManager
import com.intellij.terminal.frontend.view.TerminalView
import com.intellij.ui.content.ContentManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.TerminalOptionsProvider
import org.jetbrains.plugins.terminal.agent.TERMINAL_AI_AGENTS_REGISTRY_KEY
import org.jetbrains.plugins.terminal.agent.TerminalAgent
import org.jetbrains.plugins.terminal.agent.TerminalAgentsStateService
import org.jetbrains.plugins.terminal.agent.rpc.TerminalAgentMode
import org.jetbrains.plugins.terminal.agent.rpc.TerminalAgentsApi
import org.jetbrains.plugins.terminal.fus.ReworkedTerminalUsageCollector
import org.jetbrains.plugins.terminal.fus.TerminalStartupFusInfo
import org.jetbrains.plugins.terminal.startup.TerminalProcessType
import org.jetbrains.plugins.terminal.util.terminalProjectScope
import org.jetbrains.plugins.terminal.view.TerminalContentChangeEvent
import org.jetbrains.plugins.terminal.view.TerminalOutputModel
import org.jetbrains.plugins.terminal.view.TerminalOutputModelListener
import kotlin.time.Duration.Companion.seconds


@ApiStatus.Internal
internal data class TerminalAvailableAgentEntry(
  val terminalAgent: TerminalAgent,
  val mode: TerminalAgentMode,
)

@ApiStatus.Internal
internal fun isTerminalAgentsEnabled(): Boolean {
  return RegistryManager.getInstance().`is`(TERMINAL_AI_AGENTS_REGISTRY_KEY)
}

@ApiStatus.Internal
internal fun getAvailableTerminalAgentEntries(project: Project): List<TerminalAvailableAgentEntry> {
  val availableByKey = TerminalAgentsAvailabilityService.getInstance(project)
    .getAvailableAgents()
    .associateBy { it.agentKey }
  return TerminalAgent.getAllTerminalAgents().mapNotNull { terminalAgent ->
    availableByKey[terminalAgent.agentKey]?.let { TerminalAvailableAgentEntry(terminalAgent, it.mode) }
  }
}

@ApiStatus.Internal
internal fun findAvailableTerminalAgentEntry(project: Project, agentKey: TerminalAgent.AgentKey?): TerminalAvailableAgentEntry? {
  return agentKey?.let { key ->
    getAvailableTerminalAgentEntries(project).firstOrNull { it.terminalAgent.agentKey == key }
  }
}

@ApiStatus.Internal
internal fun launchTerminalAgent(
  project: Project,
  agentKey: TerminalAgent.AgentKey,
  contentManager: ContentManager?,
  startupFusInfo: TerminalStartupFusInfo,
) {
  terminalProjectScope(project).launch {
    val launchSpec = try {
      TerminalAgentsApi.getInstance().resolveLaunchSpec(project.projectId(), agentKey)
    }
    catch(e: CancellationException) {
      throw e
    }
    catch (e: Throwable) {
      thisLogger().error(e)
      null
    }

    if (launchSpec == null) {
      TerminalAgentsAvailabilityService.getInstance(project).refreshAvailableAgents()
      return@launch
    }

    val terminalAgent = TerminalAgent.findByKey(agentKey) ?: return@launch
    withContext(Dispatchers.EDT) {
      if (project.isDisposed) return@withContext

      val tab = TerminalToolWindowTabsManager.getInstance(project).createTabBuilder()
        .shellCommand(launchSpec.command)
        .processType(TerminalProcessType.NON_SHELL)
        .tabName(terminalAgent.displayName)
        .startupFusInfo(startupFusInfo)
        .closeOnProcessTermination(
          shouldClose = TerminalOptionsProvider.instance.closeSessionOnLogout &&
            launchSpec.mode != TerminalAgentMode.INSTALL_AND_RUN
        )
        .createTab()

      if (terminalAgent.showIconInTab && terminalAgent.icon != null) {
        tab.content.putUserData(ToolWindow.SHOW_CONTENT_ICON, true)
        tab.content.icon = terminalAgent.icon
      }

      // TODO: ad hoc code for Junie, should be generalized if we want to track other agents installs
      if (agentKey.key == "junie" && launchSpec.mode == TerminalAgentMode.INSTALL_AND_RUN) {
        fusReportWhenJunieInstalled(project, terminalAgent, tab.view)
      }

      TerminalAgentsStateService.getInstance().lastLaunchedAgentKey = agentKey
      ReworkedTerminalUsageCollector.logAgentLaunched(
        project,
        agentKey,
        isInstall = launchSpec.mode == TerminalAgentMode.INSTALL_AND_RUN,
      )
    }
  }
}

@OptIn(FlowPreview::class)
private fun fusReportWhenJunieInstalled(project: Project, agent: TerminalAgent, terminalView: TerminalView) {
  terminalView.coroutineScope.launch(Dispatchers.EDT + CoroutineName("Junie installation tracking")) {
    val outputModel = terminalView.outputModels.regular

    try {
      withTimeout(60.seconds) {
        outputModel.updatesFlow()
          .sample(1.seconds)
          .map { outputModel.getText(outputModel.startOffset, outputModel.endOffset).toString() }
          .first { it.contains("Welcome to Junie") }
      }
    }
    catch (_: TimeoutCancellationException) {
      // Junie wasn't installed during 1 minute.
      return@launch
    }

    ReworkedTerminalUsageCollector.logAgentInstalled(project, agent.agentKey)
  }
}

private fun TerminalOutputModel.updatesFlow(): Flow<Unit> {
  return channelFlow {
    val disposable = Disposer.newDisposable()
    addListener(disposable, object : TerminalOutputModelListener {
      override fun afterContentChanged(event: TerminalContentChangeEvent) {
        trySend(Unit)
      }
    })
    awaitClose { Disposer.dispose(disposable) }
  }
}