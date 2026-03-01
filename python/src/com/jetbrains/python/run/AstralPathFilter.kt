// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run

import com.intellij.execution.filters.FileHyperlinkInfoBase
import com.intellij.execution.filters.Filter
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import kotlin.io.path.Path
import kotlin.io.path.pathString

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
 */
internal class AstralPathFilter(private val project: Project, private val workingDirectory: String?) : Filter, DumbAware {

  private val pattern = Regex("""( *--> )(?<file>.+):(?<line>\d+):(?<column>\d+)""")

  override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
    val lineTrimmed = line.trimEnd()
    val (prefix, file, linerGroup, columnGroup) = pattern.matchEntire(lineTrimmed)?.destructured ?: return null

    val fileName = file.replace('\\', '/')

    val virtualFile = findFileByName(fileName) ?: return null

    val hyperlinkInfo = AstralFileHyperlinkInfo(
      virtualFile = virtualFile,
      project = project,
      line = linerGroup.toInt() - 1,
      column = columnGroup.toInt() - 1,
    )

    val lineOffset = entireLength - line.length
    val linkStart = lineOffset + prefix.length
    val linkEnd = lineOffset + lineTrimmed.length

    return Filter.Result(linkStart, linkEnd, hyperlinkInfo)
  }

  private fun findFileByName(fileName: String): VirtualFile? {
    val fs = LocalFileSystem.getInstance()

    // Try as absolute path first
    var vFile = fs.findFileByPath(fileName)
    if (vFile != null) {
      return vFile
    }

    // Try relative to project base path
    val projectBasePath = project.basePath
    if (projectBasePath != null) {
      vFile = fs.findFileByPath(Path(projectBasePath, fileName).pathString)
      if (vFile != null) {
        return vFile
      }
    }

    // Try relative to working directory
    if (!workingDirectory.isNullOrBlank()) {
      vFile = fs.findFileByPath(Path(workingDirectory, fileName).pathString)
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
  ) : FileHyperlinkInfoBase(project, line, column, myUseBrowser = true)
}
