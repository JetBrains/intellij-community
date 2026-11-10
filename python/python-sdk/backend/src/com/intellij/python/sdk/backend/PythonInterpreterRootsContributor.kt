// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.sdk.backend

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * Adds class roots to an interpreter that no fact gives, such as its skeletons, the typeshed stdlib, or the bundled stubs
 * of its installed packages, see [PythonInterpreterRoots]. A contributor gives its roots on each derive, see
 * [classRoots], in the order of the contributors. A contributor whose roots change without a change of the facts asks
 * for a new derive, see [PythonInterpreterRoots.requestDerive].
 */
@ApiStatus.Internal
interface PythonInterpreterRootsContributor {
  /** The class roots that this contributor adds to [interpreter] in [project] on each derive. It must not wait. */
  fun classRoots(project: Project, interpreter: PythonInterpreter): List<Path>

  companion object {
    val EP_NAME: ExtensionPointName<PythonInterpreterRootsContributor> = ExtensionPointName("Pythonid.interpreterRootsContributor")
  }
}
