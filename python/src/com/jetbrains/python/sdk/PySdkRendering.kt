// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.ide.ui.icons.icon
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.python.sdk.backend.pyInterpreterItems
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.python.sdk.common.PyInterpreterItem
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.jetbrains.python.PyBundle
import com.jetbrains.python.isCondaVirtualEnv
import com.jetbrains.python.isNonToolVirtualEnv
import com.jetbrains.python.sdk.legacy.PythonSdkUtil.isRemote
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.JComponent

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
 * These SDKs as a UI list holds them, grouped by type, with the ones [module] cannot use dropped.
 *
 * Virtual environments, pipenv and conda environments are grouped as [PyRenderedSdkType.VIRTUALENV].
 * Remote interpreters are grouped as [PyRenderedSdkType.REMOTE]. All the others are [PyRenderedSdkType.SYSTEM].
 *
 * An interpreter that is associated with another module is dropped, and so is one that cannot be used at all. Whether
 * it can be used is what the interpreter itself answers, so this runs each of them and must not be called on the EDT.
 * The SDK is read here, while grouping, and never reaches the list: the caller gets items.
 *
 * @see Sdk.isAssociatedWithAnotherModule
 * @see com.jetbrains.python.sdk.legacy.PythonSdkUtil.isRemote
 */
@ApiStatus.Internal
suspend fun List<Sdk>.groupInterpreterItemsByTypes(module: Module?): Map<PyRenderedSdkType, List<PyInterpreterItem>> {
  val assignable = filter { !it.isAssociatedWithAnotherModule(module) }
  // `pyInterpreterItems` keeps the order it was given, so the two lists line up.
  return assignable.zip(assignable.pyInterpreterItems())
    .filter { (_, item) -> item.problem == null }
    .groupBy({ (sdk, _) -> sdk.renderedSdkType }, { (_, item) -> item })
}

/**
 * [groupInterpreterItemsByTypes] for a Java caller that cannot suspend.
 *
 * The interpreters are read under a progress over [owner], so they run off the EDT. One progress covers the whole
 * list, where before PY-91967 each row probed the file system under a modal progress of its own while it was painted.
 */
@ApiStatus.Internal
@RequiresEdt
fun List<Sdk>.groupInterpreterItemsByTypesUnderProgress(
  module: Module?,
  owner: JComponent,
): Map<PyRenderedSdkType, List<PyInterpreterItem>> =
  runWithModalProgressBlocking(ModalTaskOwner.component(owner), PyBundle.message("python.interpreters.reading.interpreters.progress")) {
    groupInterpreterItemsByTypes(module)
  }

/**
 * These SDKs as a UI list holds them, read under a progress over [owner], for a Java caller that cannot suspend.
 *
 * Order is kept, so a caller that also holds the SDKs can line the two lists up.
 */
@ApiStatus.Internal
@RequiresEdt
fun List<Sdk>.interpreterItemsUnderProgress(owner: JComponent): List<PyInterpreterItem> =
  runWithModalProgressBlocking(ModalTaskOwner.component(owner), PyBundle.message("python.interpreters.reading.interpreters.progress")) {
    pyInterpreterItems()
  }

/**
 * The same, for a caller that has no component to hang the progress on yet, such as a form constructor.
 */
@ApiStatus.Internal
@RequiresEdt
fun List<Sdk>.interpreterItemsUnderProgress(project: Project): List<PyInterpreterItem> =
  runWithModalProgressBlocking(ModalTaskOwner.project(project), PyBundle.message("python.interpreters.reading.interpreters.progress")) {
    pyInterpreterItems()
  }

/**
 * What an unexpected list entry renders as.
 *
 * Nothing to localize: the row only exists because the list was built with the wrong type, and naming what it holds is
 * what tells whoever sees it where to look.
 */
private val Any.diagnosticLabel: @NlsSafe String get() = toString()

private val Sdk.renderedSdkType: PyRenderedSdkType
  get() = when {
    isNonToolVirtualEnv || isCondaVirtualEnv -> PyRenderedSdkType.VIRTUALENV
    isRemote(this) -> PyRenderedSdkType.REMOTE
    else -> PyRenderedSdkType.SYSTEM
  }

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
      if (nullItem != null) {
        appendItem(nullItem)
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
    append(" $suffix", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
  }

  append(" $description", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)

  // Why the row is flagged, where the user can read it. Until PY-91967 this reached `idea.log` only. A renderer is
  // reused across rows, so a healthy row must clear it rather than inherit the previous tooltip.
  this@appendItem.toolTipText = problem?.reason
}
