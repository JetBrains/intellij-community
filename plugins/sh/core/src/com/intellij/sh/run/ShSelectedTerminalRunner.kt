// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.sh.run

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import org.jetbrains.annotations.ApiStatus
import java.awt.Component

@ApiStatus.Internal
interface ShSelectedTerminalRunner {
  fun run(
    project: Project,
    command: String,
    @NlsContexts.TabTitle title: String,
    runInNewTerminal: Runnable,
  ): Boolean

  fun hasTerminalStartedFrom(project: Project, sourceFileUrl: String): Boolean

  fun showChooser(
    project: Project,
    command: String,
    @NlsContexts.TabTitle title: String,
    component: Component,
    x: Int,
    y: Int,
    runInNewTerminal: Runnable,
  ): Boolean

  fun createNewTerminal(
    project: Project,
    command: String,
    workingDirectory: String,
    @NlsContexts.TabTitle title: String,
    sourceFileUrl: String?,
  )
}
