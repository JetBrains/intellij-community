// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.console

import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.python.sdk.backend.evolution.EvoPyProject
import com.intellij.python.pyproject.model.evolution.EvoPyProjectModel

/**
 * The subproject a Python Console runs for, awaiting the project structure and the SDK table when either is still
 * loading.
 *
 * From [EvoPyProjectModel.Snapshot.forFile], which every Python surface reads, so the console and the status bar can
 * never disagree about a directory. The interpreter rides along on the target, already resolved by the snapshot.
 *
 * The subproject's own root needs no answer here. It follows from [EvoPyProject.module]: the console takes its
 * working directory from the module's first content root, and `constructPyPathAndWorkingDirCommand` always puts the
 * working directory on `sys.path`.
 */
internal suspend fun resolveConsoleTarget(project: Project, file: VirtualFile?): EvoPyProject? =
  project.service<EvoPyProjectModel>().snapshot().forFile(file)

/**
 * The main subproject — the one rooted at the project's own base dir — or `null` when the project has none, or when
 * the structure has not been computed yet.
 */
internal fun mainConsoleTarget(project: Project): EvoPyProject? =
  project.service<EvoPyProjectModel>().snapshotOrNull()?.main

/**
 * The tab title of a console running on [module]: [defaultTitle] for the main subproject, the module's own name for
 * any other one.
 */
internal fun consoleTabTitle(
  project: Project,
  module: Module?,
  defaultTitle: @NlsContexts.TabTitle String,
): @NlsContexts.TabTitle String {
  if (module == null || module == mainConsoleTarget(project)?.module) return defaultTitle
  return module.name
}
