// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DEPRECATION") // `PythonInterpreter.getSdkAPI()` is the sanctioned bridge for persistence.

package com.jetbrains.python.configuration

import com.intellij.configurationStore.StoreUtil
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.getSdkAPI
import com.jetbrains.python.sdk.PythonSdkUpdater
import com.jetbrains.python.sdk.associatedModulePath
import com.jetbrains.python.sdk.setAssociationToModule
import com.jetbrains.python.sdk.setAssociationToPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path

/**
 * Local (non-target) Python interpreter with a validated non-null [binaryPath]. Production builds
 * a handle via [createOrNull], which rejects an interpreter that carries no [PythonEnvironment]
 * (broken SDK). Tests build one via [testOnly] because a mock SDK has no environment to resolve.
 * Wraps the raw [Sdk] because persistence (sdkModificator, association) still needs the platform
 * handle.
 */
@ApiStatus.Internal
@ConsistentCopyVisibility
data class PyLocalInterpreterHandle private constructor(
  val sdk: Sdk,
  val binaryPath: Path,
) {
  companion object {
    fun createOrNull(interpreter: PythonInterpreter): PyLocalInterpreterHandle? {
      val env = interpreter.pythonEnvironment ?: return null
      return PyLocalInterpreterHandle(interpreter.getSdkAPI(), env.pythonBinaryPath)
    }

    @TestOnly
    fun testOnly(sdk: Sdk, binaryPath: Path): PyLocalInterpreterHandle =
      PyLocalInterpreterHandle(sdk, binaryPath)
  }
}

/**
 * State and apply flow for [PyLocalInterpreterEditDialog].
 *
 * Two editable fields — [homePath] and [associated] — plus the persistence sequence that mirrors
 * the legacy [PythonLocalInterpreterConfigurable.apply]:
 *  1. Commit a new home path through [Sdk.sdkModificator].
 *  2. Set or clear the association through [Sdk.setAssociationToModule] / [Sdk.setAssociationToPath].
 *  3. Force-save project settings so `.idea/misc.xml` reflects the mutation on disk right away.
 *  4. Re-detect interpreter paths through [PythonSdkUpdater] when the home path changed.
 *
 * The dialog binds [homePath] and [associated] via Kotlin UI DSL v2 property references
 * (`bindText(model::homePath)` / `bindSelected(model::associated)`), which require a
 * [kotlin.reflect.KMutableProperty] — hence the `var` declarations. Tests construct the model
 * directly, mutate the fields, invoke [apply], and assert on the resulting [Sdk] state.
 *
 * [initialHomePath] is read from [PythonEnvironment.pythonBinaryPath] via the [handle], which the
 * launcher validates upfront — the model never sees a null-path interpreter.
 */
@ApiStatus.Internal
class PyLocalInterpreterEditModel(
  private val project: Project,
  handle: PyLocalInterpreterHandle,
  private val associationTarget: Module?,
) {
  private val sdk: Sdk = handle.sdk

  /**
   * Kept as [Path] end-to-end — the platform bridges (`sdkModificator.homePath: String`, DSL
   * `bindText` text fields) do their own `toString` / `Path.of` at the boundary, so the model
   * itself never carries a raw path string.
   */
  val initialHomePath: Path = handle.binaryPath
  val initialAssociated: Boolean = sdk.associatedModulePath?.isNotBlank() == true

  var homePath: Path = initialHomePath
  var associated: Boolean = initialAssociated

  /**
   * `true` when the "Associate" checkbox is meaningful on the current page. A page that provides no
   * [associationTarget] can still show the checkbox to clear an existing association, but cannot
   * establish a new one.
   */
  val canAssociate: Boolean get() = associationTarget != null || initialAssociated

  /**
   * Persists the two edits and returns `true` when at least one field changed.
   *
   * Home path and association commit as separate write actions — same behavior as the legacy
   * apply — so a failure in one does not roll the other back.
   */
  suspend fun apply(): Boolean {
    val pathChanged = homePath != initialHomePath
    val associationChanged = associated != initialAssociated
    if (!pathChanged && !associationChanged) return false

    if (pathChanged) commitHomePath()
    if (associationChanged) commitAssociation()

    // `commitChanges` updates the in-memory JDK table only. Force a save so a user inspecting
    // `.idea/misc.xml` right after OK sees the new state instead of the pre-Save one. The save
    // enters modal state, which requires the EDT — the caller may run us on any dispatcher.
    withContext(Dispatchers.EDT) {
      StoreUtil.saveDocumentsAndProjectSettings(project)
    }

    if (pathChanged) {
      PythonSdkUpdater.updateVersionAndPathsSynchronouslyAndScheduleRemaining(sdk, project)
    }
    return true
  }

  private suspend fun commitHomePath() {
    val modificator = sdk.sdkModificator
    modificator.homePath = homePath.toString()
    writeAction {
      modificator.commitChanges()
    }
  }

  private suspend fun commitAssociation() {
    if (associated) {
      val target = associationTarget ?: return
      sdk.setAssociationToModule(target)
    }
    else {
      sdk.setAssociationToPath(null)
    }
  }
}
