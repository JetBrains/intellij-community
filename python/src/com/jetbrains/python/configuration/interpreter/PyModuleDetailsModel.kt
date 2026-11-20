// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.configuration.interpreter

import com.intellij.configurationStore.StoreUtil
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus

/**
 * The "was the interpreter changed since apply" state of [PyModuleDetailsPane]: [isSdkChanged] and [markSdkApplied].
 *
 * @param initialSdkName the interpreter the module has when the editor opens, or `null` when it has none.
 */
@ApiStatus.Internal
class PyModuleDetailsModel<T : Any>(initialSdkName: T?) {

  var initialSdkName: T? = initialSdkName
    private set

  /** `true` when [chosenSdkName] differs from the current baseline [initialSdkName]. */
  fun isSdkChanged(chosenSdkName: T?): Boolean = chosenSdkName != initialSdkName

  /** Records [appliedSdkName] as the new baseline after the caller has persisted the change. */
  fun markSdkApplied(appliedSdkName: T?) {
    initialSdkName = appliedSdkName
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
