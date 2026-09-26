// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.lsp.core.utils

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SimpleModificationTracker
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap

/**
 * Counts the changes of the tool version installed in the environment of a module, for each tool.
 *
 * Which modules one server holds depends on that version, see
 * [com.intellij.python.lsp.core.PyLspServeKey], and the answer is cached. The project roots do not
 * change when a user installs or upgrades a tool, so the root tracker cannot invalidate that cache.
 * `LspPackageListener` moves this counter instead.
 *
 * One counter for each tool. A shared counter would make a ruff install drop the pyrefly answer, and
 * the next type evaluation would read the interpreter of every module again for nothing.
 */
@Service(Service.Level.PROJECT)
@ApiStatus.Internal
class PyLspToolVersionTracker {
  private val perTool = ConcurrentHashMap<String, SimpleModificationTracker>()

  private fun trackerOf(toolName: String): SimpleModificationTracker =
    perTool.computeIfAbsent(toolName) { SimpleModificationTracker() }

  /** How many times the version of [toolName] changed in some environment of the project. */
  fun counterOf(toolName: String): Long = trackerOf(toolName).modificationCount

  /** States that the version of [toolName] changed in some environment of the project. */
  fun bump(toolName: String) {
    trackerOf(toolName).incModificationCount()
  }

  companion object {
    fun getInstance(project: Project): PyLspToolVersionTracker = project.service()
  }
}
