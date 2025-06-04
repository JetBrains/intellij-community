// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run

import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.OpenFileHyperlinkInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.SystemProperties
import com.jetbrains.python.traceBackParsers.TraceBackParser
import java.io.File
import kotlin.io.path.Path

open class PythonTracebackFilter : Filter {
  protected val project: Project
  private val myWorkingDirectory: String?

  constructor(project: Project) {
    this.project = project
    myWorkingDirectory = project.basePath
  }

  constructor(project: Project, workingDirectory: String?) {
    this.project = project
    myWorkingDirectory = workingDirectory
  }

  override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
    for (parser in TraceBackParser.PARSERS) {
      val linkInTrace = parser.findLinkInTrace(line)
      if (linkInTrace == null) {
        continue
      }
      val lineNumber = linkInTrace.lineNumber
      val vFile = findFileByName(linkInTrace.fileName)

      if (vFile != null) {
        if (!vFile.isDirectory) {
          val extension = vFile.extension
          if (extension != null && extension != "py") {
            return null
          }
        }
        val hyperlink = OpenFileHyperlinkInfo(project, vFile, lineNumber - 1)
        val textStartOffset = entireLength - line.length
        val startPos = linkInTrace.startPos
        val endPos = linkInTrace.endPos
        return Filter.Result(startPos + textStartOffset, endPos + textStartOffset, hyperlink)
      }
    }
    return null
  }

  protected open fun findFileByName(fileName: String): VirtualFile? {
    val preparedName = if (fileName.startsWith("~")) {
      fileName.replaceFirst("~".toRegex(), SystemProperties.getUserHome())
    }
    else {
      fileName
    }
    var vFile = LocalFileSystem.getInstance().findFileByPath(preparedName)
    if (vFile == null && !myWorkingDirectory.isNullOrBlank()) {
      vFile = LocalFileSystem.getInstance().findFileByIoFile(File(myWorkingDirectory, preparedName))
    }
    if (vFile == null) {
      vFile = VirtualFileManager.getInstance().findFileByUrl(preparedName)
    }
    return vFile
  }
}