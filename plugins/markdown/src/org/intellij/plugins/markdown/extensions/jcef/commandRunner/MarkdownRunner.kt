// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.extensions.jcef.commandRunner

import com.intellij.execution.Executor
import com.intellij.lang.Language
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.Nls

interface MarkdownRunner {
  companion object {
    val EP_NAME: ExtensionPointName<MarkdownRunner> = ExtensionPointName.create("org.intellij.markdown.markdownRunner")
  }

  /**
   * Check if runner applicable for code fence language
   */
  fun isApplicable(language: Language?): Boolean

  fun run(command: String, project: Project, workingDirectory: String?, executor: Executor): Boolean

  @Nls
  fun title(): String
}