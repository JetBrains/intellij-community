// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.configuration.interpreter

import com.intellij.configurationStore.StoreUtil
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus

/**
 * Layout the combo shows: the ordered interpreters and a flag for the "no interpreter" placeholder.
 *
 * Generic over the interpreter [T], so [PyModuleDetailsModelTest] can pin every branch with plain values. The
 * placeholder surfaces only when the list is empty: when there are interpreters to pick from, "no interpreter" is an
 * ambiguous choice that we do not offer.
 */
@ApiStatus.Internal
data class SdkComboContents<T : Any>(
  val orderedSdkNames: List<T>,
  val includeNoInterpreter: Boolean,
)

/**
 * State and combo-content decisions for [PyModuleDetailsPane].
 *
 * Two responsibilities:
 *  - "Was the interpreter changed since apply" predicate: [isSdkChanged] / [markSdkApplied].
 *  - Combo layout: [buildComboContents] returns the ordered interpreters and a flag for the "no interpreter" row.
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
   * Rules:
   *  - Preserves [pythonSdkNames] order.
   *  - Prepends [currentModuleSdkName] when the interpreter of the module is not in [pythonSdkNames], so the combo
   *    always shows what the module uses.
   *  - Sets [SdkComboContents.includeNoInterpreter] only when the list is empty, so the combo shows a meaningful empty
   *    state. When interpreters exist, picking "no interpreter" is not offered.
   */
  fun buildComboContents(
    pythonSdkNames: List<T>,
    currentModuleSdkName: T?,
  ): SdkComboContents<T> {
    val ordered = mutableListOf<T>().apply { addAll(pythonSdkNames) }
    if (currentModuleSdkName != null && currentModuleSdkName !in ordered) {
      ordered.add(0, currentModuleSdkName)
    }
    return SdkComboContents(
      orderedSdkNames = ordered,
      includeNoInterpreter = ordered.isEmpty(),
    )
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
