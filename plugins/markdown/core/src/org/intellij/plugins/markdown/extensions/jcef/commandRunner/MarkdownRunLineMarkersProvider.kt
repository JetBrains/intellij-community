// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.extensions.jcef.commandRunner

import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiElement
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.extensions.jcef.commandRunner.CommandRunnerExtension.Companion.execute
import org.intellij.plugins.markdown.extensions.jcef.commandRunner.CommandRunnerExtension.Companion.matches
import org.intellij.plugins.markdown.injection.MarkdownCodeFenceUtils.getContent
import org.intellij.plugins.markdown.injection.aliases.CodeFenceLanguageGuesser
import org.intellij.plugins.markdown.lang.MarkdownElementTypes
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeFence
import org.intellij.plugins.markdown.util.hasType

class MarkdownRunLineMarkersProvider : RunLineMarkerContributor() {
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
        execute(e.project!!, dir, true, text, DefaultRunExecutor.getRunExecutorInstance())
      }
    }
    return Info(AllIcons.RunConfigurations.TestState.Run, arrayOf(runAction)) { MarkdownBundle.message("markdown.runner.launch.command", text) }

  }


  private fun processBlock(lang: String, element: PsiElement): Info? {
    val language = CodeFenceLanguageGuesser.guessLanguageForInjection(lang)
    val runner = MarkdownRunner.EP_NAME.extensionList.firstOrNull { it.isApplicable(language) } ?: return null
    val text = getContent(element.parent as MarkdownCodeFence, false)
      ?.fold(StringBuilder()) { acc, psiElement -> acc.append(psiElement.text) }
      .toString()
    val dir = element.containingFile.virtualFile.parent?.path ?: return null
    val runAction = object : AnAction({ runner.title() }, AllIcons.RunConfigurations.TestState.Run_run) {
      override fun actionPerformed(event: AnActionEvent) {
        val project = event.getRequiredData(CommonDataKeys.PROJECT)
        TrustedProjectUtil.executeIfTrusted(project) {
          runner.run(text, project, dir, DefaultRunExecutor.getRunExecutorInstance())
        }
      }
    }
    return Info(AllIcons.RunConfigurations.TestState.Run_run, { runner.title() }, runAction)
  }

  private fun getText(element: PsiElement): @NlsSafe String {
    if (element.hasType(MarkdownTokenTypes.CODE_FENCE_CONTENT)) return element.text.trim()
    if (element.hasType(MarkdownTokenTypes.BACKTICK)) {
      val codeSpanText = element.parent.text
      return codeSpanText.substring(1, codeSpanText.length - 1).trim()
    }
    return ""
  }
}
