// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.configuration.interpreter

import org.jetbrains.annotations.ApiStatus

/**
 * Layout the combo shows: an ordered set of SDK names + a flag for the "no interpreter" placeholder.
 *
 * Kept name-typed on purpose — the pane maps names to editable clones (from `ProjectSdksModel`),
 * but the ordering / placeholder decision is instance-free so [PyModuleDetailsModelTest] can pin
 * every branch without touching an SDK fixture. Placeholder only surfaces when the SDK list is
 * empty; when there are real SDKs to pick from, "no interpreter" is an ambiguous choice that we
 * don't offer.
 */
@ApiStatus.Internal
data class SdkComboContents(
  val orderedSdkNames: List<String>,
  val includeNoInterpreter: Boolean,
)

/**
 * State and combo-content decisions for [PyModuleDetailsPane].
 *
 * Two responsibilities:
 *  - "Was the SDK changed since apply" predicate: [isSdkChanged] / [markSdkApplied]. Compares by
 *    [com.intellij.openapi.projectRoots.Sdk.getName] because the pane operates on editable clones;
 *    the clone and the registered SDK share a name but not instance identity.
 *  - Combo layout: [buildComboContents] returns the ordered list of SDK names and a flag for the
 *    "no interpreter" row. The pane materializes each name into `SdkComboItem.Existing(sdk)`.
 *
 * @param initialSdkName name of the SDK the module has when the editor opens (`null` when the
 *   module has no Python SDK).
 */
@ApiStatus.Internal
class PyModuleDetailsModel(initialSdkName: String?) {

  var initialSdkName: String? = initialSdkName
    private set

  /** `true` when [chosenSdkName] differs from the current baseline [initialSdkName]. */
  fun isSdkChanged(chosenSdkName: String?): Boolean = chosenSdkName != initialSdkName

  /** Records [appliedSdkName] as the new baseline after the caller has persisted the change. */
  fun markSdkApplied(appliedSdkName: String?) {
    initialSdkName = appliedSdkName
  }

  /**
   * Rules:
   *  - Preserves [pythonSdkNames] order — the pane passes the association-filtered list.
   *  - Prepends [currentModuleSdkName] when the module's own SDK is not in [pythonSdkNames]
   *    (uv-workspace case: the association filter stripped the shared venv).
   *  - Sets [SdkComboContents.includeNoInterpreter] only when no SDK names remain, so the combo
   *    shows a meaningful empty state before the "All Interpreters…" sentinel. When SDKs exist,
   *    picking "no interpreter" is not offered — the user commits to one of the available rows.
   */
  fun buildComboContents(
    pythonSdkNames: List<String>,
    currentModuleSdkName: String?,
  ): SdkComboContents {
    val ordered = mutableListOf<String>().apply { addAll(pythonSdkNames) }
    if (currentModuleSdkName != null && currentModuleSdkName !in ordered) {
      ordered.add(0, currentModuleSdkName)
    }
    return SdkComboContents(
      orderedSdkNames = ordered,
      includeNoInterpreter = ordered.isEmpty(),
    )
  }
}
