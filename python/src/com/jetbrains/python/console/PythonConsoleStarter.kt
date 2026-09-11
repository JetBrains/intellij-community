// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("PythonConsoleStarter")

package com.jetbrains.python.console

import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.EDT
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import com.jetbrains.python.run.asyncPromise
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.TestOnly
import java.util.function.Consumer

/**
 * Schedules a console runner to be built, and hands it to [andThen] on the EDT once it is there.
 *
 * The runner reaches [andThen] on the EDT because every method it offers there — `run`, `runSync`, `open` — touches
 * the tool window.
 */
@ApiStatus.Internal
fun launchPythonConsoleRunner(project: Project, contextModule: Module?, andThen: Consumer<PydevConsoleRunner>) {
  launch(project, contextModule, onlyWithInterpreter = false, andThen)
}

/**
 * [launchPythonConsoleRunner], and nothing at all when the project has no interpreter to run on.
 *
 * For the two places a console starts although nobody asked for one: opening the Python Console tool window, and
 * activating it while it holds no console. Neither is an action, so neither can grey itself out. Without the check
 * the runner starts, finds no interpreter, and throws `ExecutionException` into an error panel with a warning in
 * the log — see `PydevConsoleRunnerImpl.runSync` (PY-92174).
 */
@ApiStatus.Internal
fun launchPythonConsoleRunnerIfInterpreterExists(project: Project, contextModule: Module?, andThen: Consumer<PydevConsoleRunner>) {
  launch(project, contextModule, onlyWithInterpreter = true, andThen)
}

private fun launch(
  project: Project,
  contextModule: Module?,
  onlyWithInterpreter: Boolean,
  andThen: Consumer<PydevConsoleRunner>,
) {
  asyncPromise(project) {
    if (onlyWithInterpreter && findPythonSdkAndModule(project, contextModule).first == null) return@asyncPromise
    val runner = PyConsoleRunnerFactoryAsync.getInstance().createConsoleRunnerAsync(project, contextModule)
    withContext(Dispatchers.EDT) { andThen.accept(runner) }
  }
}

/**
 * States on [presentation] whether a Python Console can start, and says why not when it cannot.
 *
 * A console needs an interpreter, so an action that starts one greys out without it. A grey entry that says nothing
 * reads as a defect, and in a monorepo it is the state the project opens in: the startup autoconfiguration registers
 * no SDK for a project with more than one Python module (PY-92174). So [reason] goes on both the description, which
 * the status bar shows, and [ActionUtil.TOOLTIP_TEXT], which the menu item shows on hover.
 *
 * [defaultDescription] is the action's own description, restored once an interpreter appears. A `Presentation` is
 * reused across updates, so a reason left over from an earlier one would otherwise outlive it.
 */
@ApiStatus.Internal
fun setConsoleInterpreterState(
  presentation: Presentation,
  hasInterpreter: Boolean,
  reason: @Nls String,
  defaultDescription: @Nls String?,
) {
  presentation.isEnabled = hasInterpreter
  presentation.description = if (hasInterpreter) defaultDescription else reason
  presentation.putClientProperty(ActionUtil.TOOLTIP_TEXT, if (hasInterpreter) null else reason)
}

/**
 * Whether a Python Console has an interpreter to start on.
 *
 * For an action's `update`, which cannot suspend. Blocks, so it belongs on a background thread only: an action that
 * calls it has to declare [com.intellij.openapi.actionSystem.ActionUpdateThread.BGT].
 */
@ApiStatus.Internal
@RequiresBackgroundThread(generateAssertion = false /* IJPL-115548 */)
@RequiresBlockingContext
fun hasPythonConsoleInterpreter(project: Project, contextModule: Module?): Boolean =
  runBlockingCancellable { findPythonSdkAndModule(project, contextModule).first != null }

@TestOnly
@ApiStatus.Internal
@RequiresBackgroundThread(generateAssertion = false /* IJPL-115548 */)
fun createConsoleRunnerForTest(project: Project, contextModule: Module?): PydevConsoleRunner =
  @Suppress("SSBasedInspection") runBlocking {
    PyConsoleRunnerFactoryAsync.getInstance().createConsoleRunnerAsync(project, contextModule)
  }
