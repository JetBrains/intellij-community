// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.console

import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.evolution.EvoPyProject
import com.intellij.python.sdk.backend.getInterpreter
import com.jetbrains.python.project.PyProject
import com.jetbrains.python.project.PyProject.Companion.asPyProject
import com.intellij.python.pyproject.model.evolution.EvoPyProjectModel

/**
 * The subproject a Python Console runs for, and its interpreter.
 *
 * Both answers come from where the interpreter widget takes them, so the console and the status bar can never
 * disagree about a directory.
 *
 * The subproject's own root needs no field here. It follows from [module]: the console takes its working directory
 * from the module's first content root, and `constructPyPathAndWorkingDirCommand` always puts the working directory
 * on `sys.path`.
 */
internal class PyConsoleTarget(val pyProject: PyProject, val interpreter: PythonInterpreter?) {
  /**
   * The module the console runs on.
   *
   * `residesOnModule` asks not to be used unless necessary, and here it is: both
   * [PythonConsoleRunnerFactory.createConsoleRunner] and [consoleTabTitle] take a `Module`.
   */
  val module: Module get() = pyProject.residesOnModule
}

/**
 * The subproject [file] belongs to, by the widget's rule (`EvoPySdkStatusBarWidget.targetFor`):
 *
 * * a file in a Python module speaks for that module's subproject;
 * * a file in no module at all — a scratch, a file dragged in from outside — and no file at all, both fall back to
 *   the main subproject, the one rooted at the project's own base dir;
 * * a file in a module that is not Python has no target, so that a mixed project never lends an unrelated interpreter.
 */
private fun EvoPyProjectModel.Snapshot.targetFor(project: Project, file: VirtualFile?): EvoPyProject? {
  if (file == null) return main
  val module = ModuleUtilCore.findModuleForFile(file, project) ?: return main
  return forModule(module)
}

/**
 * [targetFor] plus its interpreter, awaiting the project structure and the SDK table when either is still loading.
 *
 * The target arrives as an `EvoPyProject`, which keeps its own [PyProject] private, so the [PyProject] is asked for
 * again by module. One extra lookup, and it is what lets everything above hold the wrapper.
 */
internal suspend fun resolveConsoleTarget(project: Project, file: VirtualFile?): PyConsoleTarget? {
  val snapshot = project.service<EvoPyProjectModel>().snapshot()
  // Only the module lookup touches the project model; the snapshot itself is a plain map.
  val target = readAction { snapshot.targetFor(project, file) } ?: return null
  val pyProject = target.module.asPyProject() ?: return null
  return PyConsoleTarget(pyProject, pyProject.getInterpreter())
}

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
