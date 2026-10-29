// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.configuration.interpreter

import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
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
 * Owns [PyWorkspaceStructureConfigurable]'s reaction to `ProjectJdkTable` events.
 *
 * The model is a [ProjectJdkTable.Listener] itself — the configurable subscribes it to the topic
 * and the model owns the decision + pool-resync + "combos need a repaint" signals. Platform fires
 * JDK-table events from inside write actions, so the configurable injects an EDT [dispatcher] that
 * defers the model-level work until the write action releases (PY-92598).
 *
 * The pool is abstracted behind [SdksTable] so [PyWorkspaceStructureModelTest] pins every branch
 * with a fake table + a synchronous dispatcher, without a project or a real `ProjectSdksModel`.
 */
@ApiStatus.Internal
class PyWorkspaceStructureModel(
  private val sdksTable: SdksTable,
  /** Called with every model-level task so the configurable can hop to the EDT. Default: run inline (tests). */
  private val dispatcher: (Runnable) -> Unit = Runnable::run,
  private val onSdksChanged: () -> Unit,
) : ProjectJdkTable.Listener {

  /** Editable-SDKs pool the model asks the UI to re-read on JDK-table events. */
  interface SdksTable {
    /** Names of SDKs already tracked in the pool. */
    fun trackedNames(): Set<SdkName>

    /** Rebuilds the editable-SDK pool from the live `ProjectJdkTable`. */
    fun syncFromJdkTable()
  }

  override fun jdkAdded(jdk: Sdk) {
    val name = SdkName(jdk.name)
    dispatcher.invoke { onJdkAdded(name) }
  }

  override fun jdkRemoved(jdk: Sdk) {
    val name = SdkName(jdk.name)
    dispatcher.invoke { onJdkRemoved(name) }
  }

  override fun jdkNameChanged(jdk: Sdk, previousName: String) {
    dispatcher.invoke { onJdkRenamed() }
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
