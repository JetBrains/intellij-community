// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.extensions.jcef.commandRunner

import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.nextLeaf
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.MarkdownUsageCollector.RUNNER_EXECUTED
import org.intellij.plugins.markdown.extensions.MarkdownCodeSpanConfigurationContextSearcher
import org.intellij.plugins.markdown.extensions.jcef.commandRunner.CommandRunnerExtension.Companion.execute
import org.intellij.plugins.markdown.extensions.jcef.commandRunner.CommandRunnerExtension.Companion.matches
import org.intellij.plugins.markdown.extensions.jcef.commandRunner.CommandRunnerExtension.Companion.trimPrompt
import org.intellij.plugins.markdown.injection.aliases.CodeFenceLanguageGuesser
import org.intellij.plugins.markdown.lang.MarkdownElementTypes
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeFence
import org.intellij.plugins.markdown.lang.psi.util.hasType

internal class MarkdownRunLineMarkersProvider: RunLineMarkerContributor(), DumbAware {
  override fun getInfo(element: PsiElement): Info? {
    if (!CommandRunnerExtension.isExtensionEnabled() || !element.isValid) {
      return null
    }

    if (element.hasType(MarkdownTokenTypes.FENCE_LANG)) {
      val lang = element.text?.trim()
      if (!lang.isNullOrEmpty()) {
        return processBlock(lang, element)
      }
    }

    val inCodeSpan = isOpeningCodeSpanBacktick(element)
    if (!(element.hasType(MarkdownTokenTypes.CODE_FENCE_CONTENT)
          || inCodeSpan)) {
      return null
    }

    val text = getText(element)
    if (inCodeSpan) {
      val codeSpanInfo = processCodeSpan(element, text)
      if (codeSpanInfo != null) return codeSpanInfo
    }

    val dir = getMarkdownCommandWorkingDirectory(element.project, element.containingFile.virtualFile) ?: return null
    if (!matches(element.project, dir, true, text)) {
      return null
    }

    val runAction = object : AnAction({ MarkdownBundle.message("markdown.runner.launch.command", text) },
      AllIcons.RunConfigurations.TestState.Run) {
      override fun actionPerformed(e: AnActionEvent) {
        execute(e.project!!, dir, true, text, DefaultRunExecutor.getRunExecutorInstance(), RunnerPlace.EDITOR)
      }
    }
    return Info(AllIcons.RunConfigurations.TestState.Run, arrayOf(runAction)) { MarkdownBundle.message("markdown.runner.launch.command", text) }
  }

  private fun collectFenceText(element: MarkdownCodeFence): String? {
    val fenceElements = MarkdownCodeFence.obtainFenceContent(element, false) ?: return null
    return trimPrompt(fenceElements.joinToString(separator = "") { it.text })
  }

  private fun processBlock(lang: String, element: PsiElement): Info? {
    val language = CodeFenceLanguageGuesser.guessLanguageForInjection(lang)
    val runner = MarkdownRunner.EP_NAME.extensionList.firstOrNull { it.isApplicable(language) } ?: return null
    val text = (element.parent as? MarkdownCodeFence)?.let(this::collectFenceText) ?: return null
    val dir = getMarkdownCommandWorkingDirectory(element.project, element.containingFile.virtualFile) ?: return null
    val runAction = object : AnAction({ runner.title() }, AllIcons.RunConfigurations.TestState.Run_run) {
      override fun actionPerformed(event: AnActionEvent) {
        val project = event.getData(CommonDataKeys.PROJECT) ?: return
        TrustedProjectUtil.executeIfTrusted(project) {
          RUNNER_EXECUTED.log(project, RunnerPlace.EDITOR, RunnerType.BLOCK, runner.javaClass)
          runner.run(text, project, dir, DefaultRunExecutor.getRunExecutorInstance())
        }
      }
    }
    return Info(AllIcons.RunConfigurations.TestState.Run_run, arrayOf(runAction)) { runner.title() }
  }

  private fun processCodeSpan(element: PsiElement, elementText: String): Info? {
    val text = elementText.trim()
    if (text.isBlank()) return null

    val codeSpans = getAllCodeSpansOnLine(element)
    if (codeSpans.firstOrNull() != element) return null

    val configurations = codeSpans
      .asSequence()
      .map { getText(it).trim() to it }
      .filter { it.first.isNotBlank() }
      .distinctBy { it.first }
      .flatMap { (text, host) ->
        MarkdownCodeSpanConfigurationContextSearcher
          .findAllConfigurations(text, host)
          .mapNotNull { it.findExisting() ?: it.getConfiguration() }
          .distinctBy { it.uniqueID }
      }
      .toList()
    if (configurations.isEmpty()) return null

    val actions = configurations.map(::RunConfigurationAction).toTypedArray()
    return Info(
      AllIcons.RunConfigurations.TestState.Run_run,
      actions
    ) { MarkdownBundle.message("markdown.runner.launch.command", text) }
  }

  private fun getText(element: PsiElement): @NlsSafe String {
    if (element.hasType(MarkdownTokenTypes.CODE_FENCE_CONTENT)) return element.text.trim()
    if (element.hasType(MarkdownTokenTypes.BACKTICK)) {
      val codeSpanText = element.parent.text
      return codeSpanText.substring(1, codeSpanText.length - 1).trim()
    }
    return ""
  }

  private fun getAllCodeSpansOnLine(element: PsiElement): List<PsiElement> {
    val file = element.containingFile ?: return emptyList()
    val document = PsiDocumentManager.getInstance(element.project).getDocument(file) ?: return listOf(element)

    val line = document.getLineNumber(element.textRange.startOffset)
    val lineStart = document.getLineStartOffset(line)
    val lineEnd = document.getLineEndOffset(line)

    val result = mutableListOf<PsiElement>()
    var leaf = file.findElementAt(lineStart)

    while (leaf != null) {
      val leafRange = leaf.textRange
      if (leafRange.startOffset >= lineEnd) break
      if (leafRange.endOffset > lineStart && isOpeningCodeSpanBacktick(leaf) && getText(leaf).isNotBlank()) {
        result.add(leaf)
      }
      leaf = leaf.nextLeaf()
    }

    return result
  }

  private fun isOpeningCodeSpanBacktick(element: PsiElement): Boolean =
    element.hasType(MarkdownTokenTypes.BACKTICK)
    && element.parent.hasType(MarkdownElementTypes.CODE_SPAN)
    && element.parent.firstChild == element


  private class RunConfigurationAction(private val settings: RunnerAndConfigurationSettings): AnAction({ settings.name }, settings.configuration.icon) {
    override fun actionPerformed(event: AnActionEvent) {
      val project = event.project ?: settings.configuration.project
      TrustedProjectUtil.executeIfTrusted(project) {
        val runManager = RunManager.getInstance(project)
        if (!runManager.hasSettings(settings)) {
          runManager.setTemporaryConfiguration(settings)
        }
        if (runManager.shouldSetRunConfigurationFromContext()) {
          runManager.selectedConfiguration = settings
        }
        ExecutionUtil.runConfiguration(settings, DefaultRunExecutor.getRunExecutorInstance())
      }
    }
  }
}
