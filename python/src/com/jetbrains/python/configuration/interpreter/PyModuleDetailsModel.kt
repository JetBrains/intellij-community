// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.configuration.interpreter

import com.intellij.configurationStore.StoreUtil
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus

/**
 * The interpreter state of [PyModuleDetailsPane]: the interpreter of the module as the baseline, see [isSdkChanged] and
 * [markSdkApplied], and the pick of the user that is not applied yet, see [pick]. The combo selects what [selectionFor]
 * gives after each read of its rows, so a read again, such as when the combo is shown again, keeps the pick.
 *
 * @param initialSdkName the interpreter the module has when the editor opens, or `null` when it has none.
 */
@ApiStatus.Internal
class PyModuleDetailsModel<T : Any>(initialSdkName: T?) {

  var initialSdkName: T? = initialSdkName
    private set

  /** `true` when [chosenSdkName] differs from the current baseline [initialSdkName]. */
  fun isSdkChanged(chosenSdkName: T?): Boolean = chosenSdkName != initialSdkName

  /** The interpreter that the user picked and did not apply yet, or `null` for no pick, see [pick]. */
  private var picked: T? = null

  /** Records [appliedSdkName] as the new baseline after the caller has persisted the change. The pick is applied then. */
  fun markSdkApplied(appliedSdkName: T?) {
    initialSdkName = appliedSdkName
    dropPick()
  }

  /**
   * Records [chosen], the interpreter that the user selected. A pick of the baseline is no pick, and so is `null`: the
   * combo has no row for no interpreter, and it selects nothing only while it replaces its rows.
   */
  fun pick(chosen: T?) {
    picked = chosen.takeIf { it != initialSdkName }
  }

  /**
   * Makes [moduleSdk], the interpreter that the module has now, the baseline, and returns the interpreter to select: the
   * pick of the user, else [moduleSdk].
   */
  fun selectionFor(moduleSdk: T?): T? {
    initialSdkName = moduleSdk
    if (picked == moduleSdk) dropPick()
    return picked ?: moduleSdk
  }

  /** Drops the pick of the user, for a reset of the page. */
  fun dropPick() {
    picked = null
  }

  /**
   * Flush project settings so `.iml` files reflect in-memory root-model mutations on disk right
   * after Apply, not after the next unrelated auto-save. Without this, a user inspecting the
   * `.iml` immediately after Apply still sees the pre-Apply file. Caller gates on dirtiness.
   */
  @RequiresEdt
  internal fun flushSettings(project: Project) {
    StoreUtil.saveDocumentsAndProjectSettings(project)
  }
}
