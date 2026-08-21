// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pytools

import com.intellij.execution.Platform

/**
 * Something with a resolvable executable whose custom path and auto-detection are managed per Eel
 * machine (via the per-machine custom-path store and [PyExecutableCache]). [PyTool] is one implementation, but
 * a `PyExecutable` need not be a `PyTool` — e.g. conda participates in the same custom-path/cache
 * mechanism without being a `PyTool` extension.
 */
interface PyExecutable {
  /**
   * Stable identifier used as the key for this executable's custom path and detection cache. For a
   * [PyTool] this is its package name; it must equal the command name in [toolCommandSpec] so custom
   * paths and detection resolve to the same store entry.
   */
  val fusId: String

  /**
   * How to auto-detect this executable: its command name ([fusId]) plus the directories to search
   * beyond `PATH`. Defaults to the common per-user install dirs; tools with extra well-known
   * locations (poetry, conda) override.
   */
  val toolCommandSpec: ToolCommandSpec get() = pyExecutableSpec(fusId)
}

/** Well-known per-user install dirs that tool installers use but that are frequently off `PATH`. */
private val KNOWN_SEARCH_PATHS: List<ToolSearchPath> = listOf(
  ToolSearchPath.RelativePathFromHome(listOf(".local", "bin")),
  ToolSearchPath.RelativePath("APPDATA", listOf("Python", "Scripts"), Platform.WINDOWS),
  ToolSearchPath.RelativePath("LOCALAPPDATA", listOf("Python", "Scripts"), Platform.WINDOWS),
)

/**
 * A [ToolCommandSpec] for command [name] that searches `PATH`, the common [KNOWN_SEARCH_PATHS], and any
 * [extraPaths]. Used to build [PyExecutable.toolCommandSpec] and to detect bare executables that have no
 * dedicated [PyExecutable] type.
 */
fun pyExecutableSpec(name: String, extraPaths: List<ToolSearchPath> = emptyList()): ToolCommandSpec =
  ToolCommandSpec(name, KNOWN_SEARCH_PATHS + extraPaths)

/**
 * A plain [PyExecutable] identified only by its command [fusId] — for a [PyTool]'s secondary entry
 * points that ship with it (e.g. uv also provides `uvx`), declared via [PyTool.executables]. The tool
 * that owns it keeps the naming; the module owning that tool calls this rather than pytools defining a
 * type per executable.
 */
fun pyExecutable(fusId: String): PyExecutable = NamedPyExecutable(fusId)

private data class NamedPyExecutable(override val fusId: String) : PyExecutable
