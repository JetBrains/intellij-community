// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.sdk.backend

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.errorProcessing.PyResult

/**
 * Reads one kind of Python environment off the file system layout around an interpreter.
 *
 * A tool that makes its own kind of environment contributes a provider from its own module, so this module holds no
 * knowledge of venv or conda. The detector asks each provider in the registered order and takes the first answer.
 */
interface PythonEnvironmentProvider {
  /**
   * The class this provider builds.
   *
   * The core matches an environment against it to name the environment, so no environment carries its own name. See
   * [kindId].
   */
  val environmentClass: Class<out PythonEnvironment>

  /**
   * The environment around [pythonBinary], or null when the layout is not this provider's kind.
   *
   * The provider finds the environment root itself, because where the root sits depends on the kind. A virtual
   * environment on Windows keeps the interpreter in `Scripts`, and a conda environment keeps it in the root.
   *
   * Null and a failure mean different things. Null says "another provider owns this layout". A failure says "this
   * layout is mine and it is broken", which stops the search.
   */
  @RequiresBackgroundThread
  fun detect(pythonBinary: PythonBinary): PyResult<PythonEnvironment>?

  companion object {
    val EP_NAME: ExtensionPointName<PythonEnvironmentProvider> = ExtensionPointName.create("Pythonid.pythonEnvironmentProvider")
  }
}
