// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.extensions.jcef.commandRunner

import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.SystemInfo
import com.intellij.psi.PsiElement
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.MarkdownUsageCollector.RUNNER_EXECUTED
import org.intellij.plugins.markdown.extensions.jcef.commandRunner.CommandRunnerExtension.Companion.execute
import org.intellij.plugins.markdown.extensions.jcef.commandRunner.CommandRunnerExtension.Companion.matches
import org.intellij.plugins.markdown.extensions.jcef.commandRunner.CommandRunnerExtension.Companion.trimPrompt
import org.intellij.plugins.markdown.injection.aliases.CodeFenceLanguageGuesser
import org.intellij.plugins.markdown.lang.MarkdownElementTypes
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeFence
import org.intellij.plugins.markdown.lang.psi.util.hasType
import java.awt.Dimension
import java.util.*
import kotlin.collections.ArrayList

internal class MarkdownRunLineMarkersProvider: RunLineMarkerContributor() {
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
    val inCodeSpan = (element.hasType(MarkdownTokenTypes.BACKTICK)
                      && element.parent.hasType(MarkdownElementTypes.CODE_SPAN)
                      && element.parent.firstChild == element)
    if (!(element.hasType(MarkdownTokenTypes.CODE_FENCE_CONTENT)
          || inCodeSpan)) {
      return null
    }

    val dir = element.containingFile.virtualFile.parent?.path ?: return null
    val text = getText(element)
    if (!matches(element.project, dir, true, text, allowRunConfigurations = inCodeSpan)) {
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
    val dir = element.containingFile.virtualFile.parent?.path ?: return null
    val runAction = object : AnAction({ runner.title() }, AllIcons.RunConfigurations.TestState.Run_run) {
      override fun actionPerformed(event: AnActionEvent) {
        val project = event.getData(CommonDataKeys.PROJECT) ?: return
        TrustedProjectUtil.executeIfTrusted(project) {
          RUNNER_EXECUTED.log(project, RunnerPlace.EDITOR, RunnerType.BLOCK, runner.javaClass)
          val command = collectCommand(project, text) ?: return@executeIfTrusted
          runner.run(command, project, dir, DefaultRunExecutor.getRunExecutorInstance())
        }
      }
    }
    return Info(AllIcons.RunConfigurations.TestState.Run_run, { runner.title() }, runAction)
  }

  /**
   * @return command with `%placeholders%` replaced with values from the user,
   *         or null if the user has canceled the placeholder dialog.
   */
  private fun collectCommand(project: Project, text: String): String? {
    val placeholders = collectPlaceholders(text)
    if (placeholders.isEmpty()) {
      return text
    }

    val placeholdersValues = PlaceholdersDialog.getPlaceholders(project, placeholders) ?: return null

    return placeholdersValues.entries.fold(text) { command, entry ->
      command.replace("%${entry.key}%", entry.value)
    }
  }

  private class PlaceholdersDialog(project: Project, private val placeholders: Set<String>) : DialogWrapper(project) {
    private val values = ArrayList<String>(Collections.nCopies(placeholders.size, ""))

    init {
      title = MarkdownBundle.message("markdown.runner.launch.placeholder.dialog.title")
      init()
    }

    override fun createCenterPanel() = panel {
      placeholders.forEachIndexed { index, placeholder ->
        row("$placeholder:") {
          expandableTextField()
            .bindText({ values[index] }, { values[index] = it })
            .horizontalAlign(HorizontalAlign.FILL)
        }
      }
    }.apply {
      preferredSize = Dimension(300, 30)
    }

    companion object {
      /**
       * @return map of placeholders and their values or null if dialog was cancelled.
       */
      fun getPlaceholders(project: Project, placeholders: Set<String>): Map<String, String>? {
        val dialog = PlaceholdersDialog(project, placeholders)
        if (!dialog.showAndGet()) {
          return null
        }
        return placeholders.zip(dialog.values).toMap()
      }
    }
  }

  /**
   * @return set of placeholders in the given command.
   */
  private fun collectPlaceholders(command: String): Set<String> {
    val isWindows = SystemInfo.isWindows
    return PLACEHOLDER_REGEXP.findAll(command)
      .mapNotNull {
        val raw = it.groupValues[0]
        val name = raw.substring(1, raw.length - 1)

        // On Windows syntax %NAME% is used for ENV variables, so skip the defined ones.
        if (isWindows) {
          val envValue = System.getenv(name)
          if (envValue != null) {
            return@mapNotNull null
          }
        }

        name
      }.toSet()
  }

  private fun getText(element: PsiElement): @NlsSafe String {
    if (element.hasType(MarkdownTokenTypes.CODE_FENCE_CONTENT)) return element.text.trim()
    if (element.hasType(MarkdownTokenTypes.BACKTICK)) {
      val codeSpanText = element.parent.text
      return codeSpanText.substring(1, codeSpanText.length - 1).trim()
    }
    return ""
  }

  companion object {
    private val PLACEHOLDER_REGEXP = Regex("%([\\w\\d_\\-]+)%")
  }
}
