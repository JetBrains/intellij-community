// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pixi.backend.workspace

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.readText

internal data class PixiWorkspace(val root: Path, val environments: List<String>)

internal fun findPixiWorkspace(start: Path): PixiWorkspace? {
  val initialDirectory = generateSequence(start) { it.parent }.firstOrNull { it.exists() && it.isDirectory() } ?: return null
  var nearestManifest: PixiManifest? = null
  for (directory in generateSequence(initialDirectory) { it.parent }) {
    val manifest = readManifest(directory) ?: continue
    if (nearestManifest == null) nearestManifest = manifest
    if (manifest.isWorkspace) return manifest.toWorkspace()
  }
  return nearestManifest?.toWorkspace()
}

private data class PixiManifest(val root: Path, val table: PixiTomlTable, val isPyProject: Boolean, val isWorkspace: Boolean) {
  fun toWorkspace(): PixiWorkspace {
    val prefix = if (isPyProject) "tool.pixi." else ""
    val environments = table.getTable("${prefix}environments")?.keySet().orEmpty()
    return PixiWorkspace(root, listOf(DEFAULT_ENVIRONMENT) + (environments - DEFAULT_ENVIRONMENT).sorted())
  }
}

private fun readManifest(directory: Path): PixiManifest? {
  val pixi = directory.resolve("pixi.toml")
  if (pixi.exists()) {
    val table = parse(pixi) ?: return null
    return PixiManifest(directory, table, false, table.getTable("workspace") != null || table.getTable("project") != null)
  }
  val pyproject = directory.resolve("pyproject.toml")
  if (pyproject.exists()) {
    val table = parse(pyproject) ?: return null
    if (table.getTable("tool.pixi") == null) return null
    return PixiManifest(directory, table, true, table.getTable("tool.pixi.workspace") != null || table.getTable("tool.pixi.project") != null)
  }
  return null
}

private fun parse(path: Path): PixiTomlTable? = parsePixiToml(path.readText())

internal const val DEFAULT_ENVIRONMENT = "default"
