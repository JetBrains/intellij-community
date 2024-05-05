// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.terminal.block.completion.spec.ShellRuntimeContext
import org.jetbrains.annotations.ApiStatus

@get:ApiStatus.Experimental
val ShellRuntimeContext.project: Project
  get() = getUserData(PROJECT_KEY) ?: error("No project data in $this")

internal val PROJECT_KEY: Key<Project> = Key.create("Project")