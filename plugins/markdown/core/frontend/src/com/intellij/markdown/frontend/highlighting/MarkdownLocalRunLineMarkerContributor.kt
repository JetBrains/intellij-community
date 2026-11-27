// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.frontend.highlighting

import com.intellij.execution.lineMarker.RunLineMarkerContributorBase
import com.intellij.execution.lineMarker.RunLineMarkerContributorBase.InfoBase
import com.intellij.icons.AllIcons
import com.intellij.ide.vfs.rpcId
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.project.projectId
import com.intellij.psi.PsiElement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.MarkdownUsageCollector.RUNNER_EXECUTED
import org.intellij.plugins.markdown.extensions.jcef.commandRunner.MarkdownCommandRunnerPreviewExtension
import org.intellij.plugins.markdown.extensions.jcef.commandRunner.MarkdownCodeFenceRunner
import org.intellij.plugins.markdown.extensions.jcef.commandRunner.MarkdownCommandWorkingDirectoryPaths
import org.intellij.plugins.markdown.extensions.jcef.commandRunner.MarkdownRunnerContext
import org.intellij.plugins.markdown.extensions.jcef.commandRunner.RunnerPlace
import org.intellij.plugins.markdown.extensions.jcef.commandRunner.RunnerType
import org.intellij.plugins.markdown.extensions.jcef.commandRunner.getMarkdownCommandWorkingDirectoryPaths
import org.intellij.plugins.markdown.extensions.jcef.commandRunner.trimMarkdownCommandPrompt
import org.intellij.plugins.markdown.injection.aliases.CodeFenceLanguageGuesser
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeFence
import org.intellij.plugins.markdown.lang.psi.util.hasType
import org.intellij.plugins.markdown.service.MarkdownCommandRunnerRemoteApi
import org.jetbrains.annotations.Nls
import java.awt.event.MouseEvent
import javax.swing.SwingUtilities

internal class MarkdownLocalRunLineMarkerContributor : RunLineMarkerContributorBase, DumbAware {
  override fun getInfo(element: PsiElement): InfoBase? {
    if (!element.isValid || !element.hasType(MarkdownTokenTypes.FENCE_LANG)) return null
    if (!MarkdownCommandRunnerPreviewExtension.isEnabled()) return null
    val fenceLanguage = element.text.trim().takeIf { it.isNotEmpty() } ?: return null
    val language = CodeFenceLanguageGuesser.guessLanguageForExecution(fenceLanguage)
    val runner = MarkdownCodeFenceRunner.findRunner(language) ?: return null
    val fence = element.parent as? MarkdownCodeFence ?: return null
    val content = MarkdownCodeFence.obtainFenceContent(fence, false)?.joinToString("") { it.text } ?: return null
    val command = if (language?.id == "PowerShell") content else trimMarkdownCommandPrompt(content, fenceLanguage)
    val sourceFile = element.containingFile.virtualFile ?: return null
    val action = RunCodeFenceAction(sourceFile, element.textOffset, fenceLanguage, command, runner.title())
    return object : InfoBase {
      override val icon = AllIcons.RunConfigurations.TestState.Run_run
      override val actions = listOf(action)
      override val tooltipProvider: (PsiElement) -> String = {
        MarkdownBundle.message("markdown.runner.launch.block.tooltip", runner.title())
      }
    }
  }

  private class RunCodeFenceAction(
    private val sourceFile: VirtualFile,
    private val offset: Int,
    private val fenceLanguage: String,
    private val command: String,
    private val runnerTitle: @Nls String,
  ) : DumbAwareAction({ runnerTitle }, AllIcons.RunConfigurations.TestState.Run_run) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
      event.presentation.text = if (event.place == ActionPlaces.EDITOR_GUTTER_POPUP) {
        MarkdownBundle.message("markdown.runner.choose.terminal")
      }
      else {
        runnerTitle
      }
    }

    override fun actionPerformed(event: AnActionEvent) {
      val project = event.project ?: return
      val editor = event.getData(CommonDataKeys.EDITOR) ?: return
      if (!sourceFile.isValid) return
      val language = CodeFenceLanguageGuesser.guessLanguageForExecution(fenceLanguage)
      val runner = MarkdownCodeFenceRunner.findRunner(language) ?: return
      val mouseEvent = event.inputEvent as? MouseEvent
      val point = if (mouseEvent == null) {
        editor.offsetToXY(offset.coerceIn(0, editor.document.textLength))
      }
      else {
        SwingUtilities.convertPoint(mouseEvent.component, mouseEvent.point, editor.contentComponent)
      }
      val showTargetChooser = event.place == ActionPlaces.EDITOR_GUTTER_POPUP
      fun run(paths: MarkdownCommandWorkingDirectoryPaths) {
        if (project.isDisposed || editor.isDisposed || !sourceFile.isValid) return
        val accepted = runner.run(
          command, project,
          MarkdownRunnerContext(
            sourceFileUrl = sourceFile.url,
            showTargetChooser = showTargetChooser,
            component = editor.contentComponent,
            x = point.x,
            y = point.y,
            workingDirectoryPaths = paths,
            fenceLanguage = fenceLanguage,
          ),
        )
        if (accepted) {
          RUNNER_EXECUTED.log(project, RunnerPlace.EDITOR, RunnerType.BLOCK, runner.javaClass)
        }
      }

      if (!MarkdownCommandRunnerRemoteApi.isConnected()) {
        val paths = getMarkdownCommandWorkingDirectoryPaths(project, sourceFile) ?: return
        run(paths)
        return
      }
      val projectId = project.projectId()
      val virtualFileId = sourceFile.rpcId()
      event.coroutineScope.launch(Dispatchers.Default) {
        val api = MarkdownCommandRunnerRemoteApi.getInstance()
        val paths = api.getWorkingDirectoryPaths(projectId, virtualFileId) ?: return@launch
        withContext(Dispatchers.EDT) { run(paths) }
      }
    }
  }
}
