// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.openapi.project.Project
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.PythonInterpreterPathsSetup

/** Sets up the paths of a new interpreter with an update, see [updateInterpreterFactsOrShowError]. */
internal class PythonInterpreterFactsPathsSetup(private val project: Project) : PythonInterpreterPathsSetup {
  override suspend fun setUpPaths(interpreter: PythonInterpreter) {
    updateInterpreterFactsOrShowError(interpreter, project)
  }
}
