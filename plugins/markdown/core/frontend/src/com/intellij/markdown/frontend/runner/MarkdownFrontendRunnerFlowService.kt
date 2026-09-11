// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.frontend.runner

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.lang.Language
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.impl.findEditorOrNull
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.platform.project.findProjectOrNull
import fleet.rpc.client.durable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.intellij.plugins.markdown.MarkdownUsageCollector.RUNNER_EXECUTED
import org.intellij.plugins.markdown.extensions.jcef.commandRunner.MarkdownRunner
import org.intellij.plugins.markdown.extensions.jcef.commandRunner.MarkdownRunnerContext
import org.intellij.plugins.markdown.extensions.jcef.commandRunner.RunnerPlace
import org.intellij.plugins.markdown.extensions.jcef.commandRunner.RunnerType
import org.intellij.plugins.markdown.service.MarkdownCommandRunnerRemoteApi
import org.intellij.plugins.markdown.service.MarkdownFrontendRunnerRequest
import org.intellij.plugins.markdown.service.confirmBackendProjectIsTrusted
import org.jetbrains.annotations.ApiStatus
import java.awt.Point
import javax.swing.SwingUtilities

@ApiStatus.Internal
@Service(Service.Level.APP)
class MarkdownFrontendRunnerFlowService(coroutineScope: CoroutineScope) {
  init {
    coroutineScope.launch {
      durable {
        val api = MarkdownCommandRunnerRemoteApi.getInstance()
        api.frontendRunnerRequests().collect { request ->
          val project = request.projectId.findProjectOrNull() ?: return@collect
          if (!confirmBackendProjectIsTrusted(project, api)) return@collect
          withContext(Dispatchers.EDT) {
            try {
              runRequest(project, request)
            }
            catch (e: Exception) {
              rethrowControlFlowException(e)
              logger<MarkdownFrontendRunnerFlowService>().warn("Failed to run a Markdown code fence.", e)
            }
          }
        }
      }
    }
  }

  private fun runRequest(project: Project, request: MarkdownFrontendRunnerRequest) {
    if (project.isDisposed) return
    val editor = request.editorId.findEditorOrNull() ?: return
    val language = request.languageId?.let { Language.findLanguageByID(it) } ?: return
    val runner = MarkdownRunner.EP_NAME.extensionList.firstOrNull { it.isApplicable(language) } ?: return
    val point = request.screenPoint?.let {
      Point(it.x, it.y).also { point -> SwingUtilities.convertPointFromScreen(point, editor.contentComponent) }
    } ?: editor.offsetToXY(request.offset.coerceIn(0, editor.document.textLength))
    RUNNER_EXECUTED.log(project, RunnerPlace.EDITOR, RunnerType.BLOCK, runner.javaClass)
    runner.run(
      request.command,
      project,
      DefaultRunExecutor.getRunExecutorInstance(),
      MarkdownRunnerContext(
        sourceFileUrl = request.sourceFileUrl,
        showTargetChooser = request.showTargetChooser,
        component = editor.contentComponent,
        x = point.x,
        y = point.y,
        workingDirectoryPaths = request.workingDirectoryPaths,
      ),
    )
  }

  internal class StartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
      service<MarkdownFrontendRunnerFlowService>()
    }
  }
}
