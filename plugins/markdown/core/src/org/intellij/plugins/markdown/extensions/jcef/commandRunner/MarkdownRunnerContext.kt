// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.extensions.jcef.commandRunner

import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import java.util.function.Consumer

@ApiStatus.Internal
data class MarkdownRunnerContext(
  val sourceFileUrl: String,
  val showTargetChooser: Boolean,
  val component: Component,
  val x: Int,
  val y: Int,
  val workingDirectoryPaths: MarkdownCommandWorkingDirectoryPaths,
) {
  fun withWorkingDirectory(project: Project, action: Consumer<String>) {
    withMarkdownCommandWorkingDirectory(project, workingDirectoryPaths, component, x, y, action::accept)
  }
}
