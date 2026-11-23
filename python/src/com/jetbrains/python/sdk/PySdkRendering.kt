// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.pyproject.model.evolution.EvoPyProjectModel
import com.jetbrains.python.project.PyProject
import com.intellij.python.sdk.backend.asItem
import com.intellij.python.sdk.backend.flavor
import com.intellij.python.sdk.backend.isTargetBased
import com.intellij.python.venv.sdk.flavors.VirtualEnvSdkFlavor
import com.jetbrains.python.sdk.flavors.conda.CondaEnvSdkFlavor
import com.intellij.python.sdk.backend.PythonInterpreterRegistry
import com.intellij.ide.ui.icons.icon
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.python.sdk.common.PyInterpreterItem
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.jetbrains.python.PyBundle
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@Nls
val noInterpreterMarker: String = "<${PyBundle.message("python.sdk.there.is.no.interpreter")}>"

/**
 * Order is important, sdks are rendered in the same order as the types are defined.
 *
 * @see groupInterpreterItemsByTypes
 */
@ApiStatus.Internal
enum class PyRenderedSdkType {
  VIRTUALENV, SYSTEM, REMOTE
}

/**
 * The interpreters that [pyProject] owns, or the interpreters of every [PyProject] of [project] when [pyProject] is
 * `null`. A run configuration offers them before the user picks its module.
 *
 * The [PyProject]s for `null` come from the [EvoPyProjectModel] snapshot, which does not wait for the JPS model. The
 * callers read under a modal progress, and a modal progress holds the synchronization with the JPS model back
 * (PY-93821).
 */
private suspend fun pyProjectInterpreters(project: Project, pyProject: PyProject?): List<PythonInterpreter> {
  val registry = PythonInterpreterRegistry.getInstance(project)
  val pyProjects = if (pyProject != null) listOf(pyProject) else EvoPyProjectModel.getInstance(project).snapshot().pyProjects()
  return pyProjects.flatMap { registry.interpreters(it) }.distinct()
}

/**
 * [pyProjectInterpreters] without the wait, for a Java form on the EDT that cannot suspend. It is empty before the first
 * computation of [PythonInterpreterRegistry], and for `null` also before the first [EvoPyProjectModel] snapshot.
 *
 * It returns the interpreters that [pyProject] owns in [PythonInterpreterRegistry], or the interpreters of every
 * [PyProject] of the snapshot when [pyProject] is `null`. A [PyProject] can own more interpreters than
 * [com.intellij.python.sdk.backend.evolution.EvoPyProject.interpreter], the one that its module uses. A member of a tool
 * workspace owns none, because it uses the interpreter of the workspace root.
 */
@ApiStatus.Internal
fun pyProjectInterpretersIfReady(project: Project, pyProject: PyProject?): List<PythonInterpreter> {
  val pyProjects = if (pyProject != null) listOf(pyProject) else EvoPyProjectModel.getInstance(project).snapshotOrNull()?.pyProjects()
  val registry = PythonInterpreterRegistry.getInstance(project)
  return pyProjects.orEmpty().flatMap { registry.interpretersIfReady(it) }.distinct()
}

private fun EvoPyProjectModel.Snapshot.pyProjects(): List<PyProject> = evoPyProjects.map { it.pyProject }.toList()

/**
 * The interpreters of [pyProject] as a UI list holds them, read under a progress over the project, for a Java caller that
 * cannot suspend. `null` means every [PyProject]. See [pyProjectInterpreters]. The rows of the list are their
 * [PythonInterpreter.asItem].
 */
@ApiStatus.Internal
@RequiresEdt
fun pyProjectInterpretersUnderProgress(project: Project, pyProject: PyProject?): List<PythonInterpreter> =
  runWithModalProgressBlocking(ModalTaskOwner.project(project), PyBundle.message("python.interpreters.reading.interpreters.progress")) {
    pyProjectInterpreters(project, pyProject)
  }

/**
 * The interpreters of the [PyProject] of [module] as the interpreter popup lists them, grouped by type. Each row pairs
 * the item with its interpreter. A row whose interpreter has a problem is dropped. See [pyProjectInterpreters].
 */
@ApiStatus.Internal
suspend fun interpreterRowsByTypes(module: Module): Map<PyRenderedSdkType, List<Pair<PyInterpreterItem, PythonInterpreter>>> {
  val pyProject = EvoPyProjectModel.getInstance(module.project).snapshot().forModule(module)?.pyProject ?: return emptyMap()
  return pyProjectInterpreters(module.project, pyProject)
    .map { it.asItem() to it }
    .filter { (item, _) -> item.problem == null }
    .groupBy { (_, interpreter) -> interpreter.renderedSdkType }
}

private val PythonInterpreter.renderedSdkType: PyRenderedSdkType
  get() = when {
    flavor.let { it is VirtualEnvSdkFlavor || it is CondaEnvSdkFlavor } -> PyRenderedSdkType.VIRTUALENV
    isTargetBased -> PyRenderedSdkType.REMOTE
    else -> PyRenderedSdkType.SYSTEM
  }

/**
 * What an unexpected list entry renders as.
 *
 * Nothing to localize: the row only exists because the list was built with the wrong type, and naming what it holds is
 * what tells whoever sees it where to look.
 */
private val Any.diagnosticLabel: @NlsSafe String get() = toString()

/**
 * Draws one row of an interpreter list.
 *
 * [value] is whatever the heterogeneous list holds: a [PyInterpreterItem], a [PySdkToInstall] offer, a plain label, or
 * `null` for the "project default" row. Everything an item needs was computed off the EDT, so this only reads.
 *
 * @param nullItem the interpreter the "project default" row stands for, or `null` to show [nullLabel] alone.
 */
internal fun SimpleColoredComponent.customizeWithSdkValue(
  value: Any?,
  nullLabel: @Nls String,
  nullItem: PyInterpreterItem?,
) {
  when (value) {
    is PySdkToInstall -> {
      value.renderInList(this)
    }
    is PyInterpreterItem -> appendItem(value)
    is String -> append(value)
    null -> {
      // The row of the current interpreter says only that, in grey, with its icon. The row of the same interpreter below
      // it names it, so this row repeats nothing.
      if (nullItem != null) {
        icon = nullItem.icon.icon()
        append(nullLabel, SimpleTextAttributes.GRAYED_ATTRIBUTES)
      }
      else {
        append(nullLabel)
      }
    }
    // A list that still holds SDKs would otherwise draw empty rows and say nothing about why.
    else -> {
      thisLogger().error("An interpreter list holds a ${value.javaClass.name}. It must hold ${PyInterpreterItem::class.java.simpleName}s.")
      append(value.diagnosticLabel)
    }
  }
}

private fun SimpleColoredComponent.appendItem(item: PyInterpreterItem) = with(item) {
  this@appendItem.icon = icon.icon()
  val problem = problem
  if (problem != null) {
    append("[${problem.marker}] $name", SimpleTextAttributes.ERROR_ATTRIBUTES)
  }
  else {
    append(name)
  }

  if (suffix != null) {
    append(" [$suffix]", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
  }

  append(" $description", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)

  // Why the row is flagged, where the user can read it. Until PY-91967 this reached `idea.log` only. A renderer is
  // reused across rows, so a healthy row must clear it rather than inherit the previous tooltip.
  this@appendItem.toolTipText = problem?.reason
}
