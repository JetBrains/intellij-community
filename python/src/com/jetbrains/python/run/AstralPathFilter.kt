// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run

import com.intellij.execution.filters.FileHyperlinkInfoBase
import com.intellij.execution.filters.Filter
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFile

/**
 * Console filter that creates hyperlinks for file paths in Ruff/ty diagnostic output.
 *
 * Matches the new cargo/clippy-style format used by Ruff and ty:
 * ```
 *   --> common/decorators.py:16:9
 * ```
 *
 * This format uses a separate line for file paths with an arrow prefix,
 * allowing multiple file references in a single diagnostic.
 *
 * A location in a Jupyter notebook names the cell, and its line and column count from the start of that cell:
 * ```
 *   --> notebooks/analysis.ipynb:cell 3:3:1
 * ```
 * The link of such a location opens the notebook at its start.
 */
internal class AstralPathFilter(private val project: Project, private val workingDirectory: String?) : Filter, DumbAware {

  private val pattern = Regex("""( *--> )(?<file>.+?)(?<cell>:cell \d+)?:(?<line>\d+):(?<column>\d+)""")

  override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
    val lineTrimmed = line.trimEnd()
    val (prefix, file, cellGroup, lineGroup, columnGroup) = pattern.matchEntire(lineTrimmed)?.destructured ?: return null

    val fileName = file.replace('\\', '/')

    val virtualFile = findFileByName(fileName) ?: return null

    val isCellLocation = cellGroup.isNotEmpty()
    val hyperlinkInfo = AstralFileHyperlinkInfo(
      virtualFile = virtualFile,
      project = project,
      line = if (isCellLocation) 0 else lineGroup.toInt() - 1,
      column = if (isCellLocation) 0 else columnGroup.toInt() - 1,
    )

    val lineOffset = entireLength - line.length
    val linkStart = lineOffset + prefix.length
    val linkEnd = lineOffset + lineTrimmed.length

    return Filter.Result(linkStart, linkEnd, hyperlinkInfo)
  }

  private fun findFileByName(fileName: String): VirtualFile? {
    val fs = StandardFileSystems.local()

    // Try as absolute path first
    var vFile = fs.findFileByPath(fileName)
    if (vFile != null) {
      return vFile
    }

    // Try relative to project base path
    val projectBasePath = project.basePath
    if (projectBasePath != null) {
      vFile = fs.findFileByPath("$projectBasePath/$fileName")
      if (vFile != null) {
        return vFile
      }
    }

    // Try relative to working directory
    if (!workingDirectory.isNullOrBlank()) {
      vFile = fs.findFileByPath("$workingDirectory/$fileName")
      if (vFile != null) {
        return vFile
      }
    }

    return null
  }

  private class AstralFileHyperlinkInfo(
    override val virtualFile: VirtualFile,
    project: Project,
    line: Int,
    column: Int,
  ) : FileHyperlinkInfoBase(project, line, column, useBrowser = true)
}
