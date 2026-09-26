// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.configuration.interpreter

import com.intellij.openapi.projectRoots.Sdk
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

/**
 * Test-friendly view over a JDK-table entry. Production wraps a real [Sdk] via [of]; tests build a
 * name-only handle via [forName] so [PyWorkspaceStructureModelTest] does not need a `Sdk` fixture
 * (the platform `Sdk` interface is non-extensible and reflection-based stubs are unreliable on CI).
 */
@ApiStatus.Internal
class PyJdkHandle private constructor(val name: String, val originalSdk: Sdk?) {
  companion object {
    fun of(sdk: Sdk): PyJdkHandle = PyJdkHandle(sdk.name, sdk)

    @TestOnly
    fun forName(name: String): PyJdkHandle = PyJdkHandle(name, null)
  }
}

/**
 * Owns [PyWorkspaceStructureConfigurable]'s reaction to `ProjectJdkTable` events. The configurable
 * delegates the three listener callbacks straight to [onJdkAdded] / [onJdkRemoved] /
 * [onJdkRenamed] — the decision, the mutation of the editable-SDKs pool, and the "combos need a
 * repaint" signal all live here.
 *
 * The pool is abstracted behind [SdksTable] so [PyWorkspaceStructureModelTest] pins every branch
 * with a fake table, without a project or a real `ProjectSdksModel`. The model itself reads only
 * [PyJdkHandle.name], so tests hand in a [PyJdkHandle.forName] view without touching [Sdk].
 */
@ApiStatus.Internal
class PyWorkspaceStructureModel(
  private val sdksTable: SdksTable,
  private val onSdksChanged: () -> Unit,
) {

  /** Editable-SDKs pool the model mutates on JDK-table events. */
  interface SdksTable {
    /** Names of SDKs already tracked in the pool. */
    fun trackedNames(): Set<String>

    /**
     * Adds an editable clone for [jdk] to the pool. Production unwraps [PyJdkHandle.originalSdk]
     * to hand to `ProjectSdksModel.addSdk`; a test fake typically records the name and drops the
     * (potentially null) original.
     */
    fun add(jdk: PyJdkHandle)

    /** Removes the editable clone whose name matches [name]. */
    fun removeByName(name: String)
  }

  fun onJdkAdded(jdk: PyJdkHandle) {
    if (jdk.name !in sdksTable.trackedNames()) sdksTable.add(jdk)
    onSdksChanged()
  }

  fun onJdkRemoved(jdk: PyJdkHandle) {
    if (jdk.name in sdksTable.trackedNames()) sdksTable.removeByName(jdk.name)
    onSdksChanged()
  }

  /** Rename does not add or remove — the SDK identity survives, only its label changes. */
  fun onJdkRenamed() {
    onSdksChanged()
  }
}
