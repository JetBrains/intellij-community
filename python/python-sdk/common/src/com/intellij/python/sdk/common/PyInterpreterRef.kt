// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.sdk.common

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls

/**
 * Opaque, serializable selector naming the interpreter the backend must apply.
 *
 * Shared by every interpreter list: the classic combos and tree carry it in [PyInterpreterItem], and the evolution
 * widget carries it in `PyEvoSdkApi.selectInterpreter` and `PyInterpreterDto`. Resolve it on the backend with
 * `PyInterpreterItem.findSdk` or `PyEvoSdkApiProvider`.
 */
@ApiStatus.Internal
@Serializable
sealed interface PyInterpreterRef {
  /** An interpreter that is already a registered PyCharm SDK, identified by its unique SDK name. */
  @Serializable
  data class ExistingSdk(val sdkName: @NonNls String) : PyInterpreterRef

  /** A detected environment that is not yet an SDK; the backend creates it from its home path on select. */
  @Serializable
  data class DetectedPath(val homePath: @NonNls String) : PyInterpreterRef

  /**
   * A declared-but-not-yet-materialized environment (poetry per-version row, hatch declared env, or an
   * "add new" version pick for uv/pip): the backend creates it via the tool's create logic, then assigns it.
   * [token] is tool-specific — poetry: the base/system Python path; hatch: the declared env name; uv: the
   * chosen Python version (empty = uv's default); pip: the chosen system Python's binary path. [folder] (uv/pip
   * only) is the env folder location (absolute path of the auto-generated first-free `.venv{X}` in the section's
   * folder); when null the backend uses the first free `.venv`, `.venv1`, … under the module base dir.
   *
   * [name] is the user-editable env name from the in-widget "add new" name field: for uv/pip it is the env **folder
   * name** created inside [folder] (the containing dir); for conda it is the **env name**. `null` keeps the tool's
   * default (the pre-filled name).
   */
  @Serializable
  data class CreateEnv(
    val token: @NonNls String,
    val folder: @NonNls String? = null,
    val name: @NonNls String? = null,
    /**
     * The Python version to install before creating anything, for a row that offered an interpreter the machine does
     * not have (see [com.intellij.python.sdk.common.evolution.EvoAddNewOptionDto.installable]). The backend installs it and then carries on with [token] pointing
     * at what landed, so a tool never has to know that installation was involved.
     */
    val installPythonVersion: @NonNls String? = null,
  ) : PyInterpreterRef

  /**
   * Configure the module's interpreter using one of the IDE's setup options (the "Shortcuts" rows — the same options
   * the "no interpreter configured" inspection ranks), identified by [toolId] (a `PyProjectSdkConfigurationExtension`
   * tool id). The backend re-resolves that option for the module and applies it: it creates the env (or, when the
   * option's tool isn't installed yet, installs the tool and then creates the env) under the SDK-configuration lock.
   */
  @Serializable
  data class Autoconfigure(val toolId: @NonNls String) : PyInterpreterRef
}
