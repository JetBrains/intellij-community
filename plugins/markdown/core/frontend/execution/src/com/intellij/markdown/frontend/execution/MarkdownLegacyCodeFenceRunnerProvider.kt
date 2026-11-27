// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.frontend.execution

import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.lang.Language
import com.intellij.openapi.project.Project
import org.intellij.plugins.markdown.extensions.jcef.commandRunner.MarkdownCodeFenceRunner
import org.intellij.plugins.markdown.extensions.jcef.commandRunner.MarkdownRunner
import org.intellij.plugins.markdown.extensions.jcef.commandRunner.MarkdownRunnerContext
import org.intellij.plugins.markdown.service.runMarkdownCommandIfTrusted
import org.jetbrains.annotations.Nls

internal class MarkdownLegacyCodeFenceRunnerProvider : MarkdownCodeFenceRunner.Provider {
  override fun findRunner(language: Language?): MarkdownCodeFenceRunner? {
    val runner = MarkdownRunner.EP_NAME.extensionList.firstOrNull { it.isApplicable(language) } ?: return null
    return LegacyCodeFenceRunner(runner)
  }

  private class LegacyCodeFenceRunner(private val delegate: MarkdownRunner) : MarkdownCodeFenceRunner {
    override fun isApplicable(language: Language?): Boolean = delegate.isApplicable(language)

    override fun run(command: String, project: Project, context: MarkdownRunnerContext): Boolean =
      runMarkdownCommandIfTrusted(project) {
        delegate.run(command, project, DefaultRunExecutor.getRunExecutorInstance(), context)
      }

    override fun title(): @Nls String = delegate.title()
  }
}
