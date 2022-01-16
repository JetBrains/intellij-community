// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.extensions.jcef.commandRunner

import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiElement
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.extensions.MarkdownExtensionsUtil
import org.intellij.plugins.markdown.extensions.jcef.commandRunner.CommandRunnerExtension.Companion.execute
import org.intellij.plugins.markdown.extensions.jcef.commandRunner.CommandRunnerExtension.Companion.matches
import org.intellij.plugins.markdown.injection.MarkdownCodeFenceUtils.getContent
import org.intellij.plugins.markdown.injection.alias.LanguageGuesser
import org.intellij.plugins.markdown.lang.MarkdownElementTypes
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeFenceImpl
import org.intellij.plugins.markdown.util.hasType

class MarkdownRunLineMarkersProvider : RunLineMarkerContributor() {

  private var commandRunnerExtension: CommandRunnerExtension.Provider? = null
  init {
    commandRunnerExtension = MarkdownExtensionsUtil.findBrowserExtensionProvider<CommandRunnerExtension.Provider>()
  }

  private fun commandRunnerExtensionEnabled(): Boolean {
    return commandRunnerExtension?.isEnabled ?: false
  }

  override fun getInfo(element: PsiElement): Info? {
    if (!commandRunnerExtensionEnabled() || !element.isValid) {
      return null
    }
    if (element.hasType(MarkdownTokenTypes.FENCE_LANG)) {
      val lang = element.text?.trim()
      if (!lang.isNullOrEmpty()) {
        return processBlock(lang, element)
      }
    }
    if (!(element.hasType(MarkdownTokenTypes.CODE_FENCE_CONTENT)
          || (element.hasType(MarkdownTokenTypes.BACKTICK)
              && element.parent.hasType(MarkdownElementTypes.CODE_SPAN)
              && element.parent.firstChild == element)
         )) {
      return null
    }

    val dir = element.containingFile.virtualFile.parent.path
    val text = getText(element)
    if (!matches(element.project, dir, true, text)) {
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
    val language = LanguageGuesser.guessLanguageForInjection(lang)
    MarkdownRunner.EP_NAME.extensionList.firstOrNull { runner ->
      runner.isApplicable(language)
    }?.let { runner ->
      val text = getContent(element.parent as MarkdownCodeFenceImpl, false)
        ?.fold(StringBuilder()) { acc, psiElement -> acc.append(psiElement.text) }
        .toString()
      val dir = element.containingFile.virtualFile.parent.path
      return Info(AllIcons.RunConfigurations.TestState.Run_run, { runner.title() }, object : AnAction() {
        override fun actionPerformed(e: AnActionEvent) {
          runner.run(text, e.project!!, dir, DefaultRunExecutor.getRunExecutorInstance())
        }
      })
    }
    return null
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
// todo: merge same line markers