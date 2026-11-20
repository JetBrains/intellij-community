// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.uv

import com.intellij.openapi.project.DumbAware
import com.intellij.python.sdk.backend.requirementsPath
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.PythonInterpreterRegistry

/**
 * Pins the [UvMode] of every uv interpreter of the project that was saved before the mode existed. See
 * [migrateLegacyUvMode].
 *
 * Runs on every project open. It skips an interpreter that stores a file. After the first run it visits only a legacy
 * plain environment again. [UvPackageManagerProvider] does the same for an interpreter that the Packages tool window
 * asks about before this activity reaches it.
 */
internal class UvModeMigrationActivity : ProjectActivity, DumbAware {
  override suspend fun execute(project: Project) {
    val legacy = PythonInterpreterRegistry.getInstance(project).allInterpreters()
      .filter { it.isUv }
      .map { it }
      .filter { it.requirementsPath == null }
    for (interpreter in legacy) {
      migrateLegacyUvMode(interpreter)
    }
  }
}
