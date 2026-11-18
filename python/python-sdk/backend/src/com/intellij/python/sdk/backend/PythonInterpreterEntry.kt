// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.sdk.backend

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.NlsSafe
import com.intellij.python.sdk.backend.impl.PythonInterpreterPresentation
import com.intellij.python.sdk.backend.impl.brokenSdkPresentation
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

/**
 * An interpreter of a project as the interpreter settings list it, see [PythonInterpreterRegistry.interpreterEntries].
 * The settings list every interpreter that the project knows, also one that names no interpreter now, so the user can
 * remove it. Remove an entry with [PythonInterpreterRegistry.remove].
 */
@ApiStatus.Internal
sealed interface PythonInterpreterEntry {
  /** An entry whose [interpreter] the registry holds. */
  class Available internal constructor(val interpreter: PythonInterpreter) : PythonInterpreterEntry

  /**
   * An entry that names no interpreter, such as an SDK whose data is broken. [reason] says why. The user can only remove
   * it, or create the interpreter again.
   */
  class Unavailable internal constructor(internal val sdk: Sdk, val reason: @Nls String) : PythonInterpreterEntry {
    /** The name that the entry had. */
    val name: @NlsSafe String get() = sdk.name

    /** The path of the Python binary that the entry named, or `null` when it named none. */
    val path: @NlsSafe String? get() = sdk.homePath

    /** What a list draws for the entry. */
    val presentation: PythonInterpreterPresentation get() = brokenSdkPresentation(sdk, reason)
  }
}
