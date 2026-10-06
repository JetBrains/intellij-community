// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.configuration.interpreter

import org.jetbrains.annotations.ApiStatus

/**
 * Typed SDK name used across the model boundary. A `String` would cross with the many other
 * `String` names in `Sdk` / `Module` / `Project`, which has already produced one silent name mix
 * (see [com.intellij.python.junit5Tests.unit.PyPackagesTreePaneNavigationTest]'s `WorkspaceMemberName`
 * guard). Keep the identity typed so a tracked-names check cannot be fed a module name by mistake.
 */
@ApiStatus.Internal
@JvmInline
value class SdkName(val value: String)

/**
 * Owns [PyWorkspaceStructureConfigurable]'s reaction to `ProjectJdkTable` events. The configurable
 * delegates the three listener callbacks straight to [onJdkAdded] / [onJdkRemoved] /
 * [onJdkRenamed] — the decision, the pool-resync signal, and the "combos need a repaint" signal
 * all live here.
 *
 * The pool is abstracted behind [SdksTable] so [PyWorkspaceStructureModelTest] pins every branch
 * with a fake table, without a project or a real `ProjectSdksModel`. The model works on SDK names
 * only, so neither it nor the tests need a real [com.intellij.openapi.projectRoots.Sdk].
 */
@ApiStatus.Internal
class PyWorkspaceStructureModel(
  private val sdksTable: SdksTable,
  private val onSdksChanged: () -> Unit,
) {

  /** Editable-SDKs pool the model asks the UI to re-read on JDK-table events. */
  interface SdksTable {
    /** Names of SDKs already tracked in the pool. */
    fun trackedNames(): Set<SdkName>

    /** Rebuilds the editable-SDK pool from the live `ProjectJdkTable`. */
    fun syncFromJdkTable()
  }

  fun onJdkAdded(name: SdkName) {
    if (name !in sdksTable.trackedNames()) sdksTable.syncFromJdkTable()
    onSdksChanged()
  }

  fun onJdkRemoved(name: SdkName) {
    if (name in sdksTable.trackedNames()) sdksTable.syncFromJdkTable()
    onSdksChanged()
  }

  /** Rename does not add or remove — the SDK identity survives, only its label changes. */
  fun onJdkRenamed() {
    onSdksChanged()
  }
}
