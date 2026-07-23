// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run

import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.OpenFileHyperlinkInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.SystemProperties
import com.jetbrains.python.traceBackParsers.LinkInTrace
import com.jetbrains.python.traceBackParsers.TraceBackParser
import java.io.File

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
    val textStartOffset = entireLength - line.length
    val resultItems = mutableListOf<Filter.ResultItem>()
    var searchStart = 0
    while (searchStart < line.length) {
      val linkInTrace = findLinkInTrace(line.substring(searchStart)) ?: break
      val startPos = searchStart + linkInTrace.startPos
      val endPos = searchStart + linkInTrace.endPos
      searchStart = endPos

      val lineNumber = linkInTrace.lineNumber
      val vFile = findFileByName(linkInTrace.fileName)
      if (vFile == null) {
        continue
      }
      if (!vFile.isDirectory && vFile.extension != null && vFile.extension != "py") {
        continue
      }

      val hyperlink = OpenFileHyperlinkInfo(project, vFile, lineNumber - 1)
      resultItems.add(Filter.ResultItem(startPos + textStartOffset, endPos + textStartOffset, hyperlink))
    }
    return if (resultItems.isEmpty()) null else Filter.Result(resultItems)
  }

  private fun findLinkInTrace(line: String): LinkInTrace? {
    var earliestLink: LinkInTrace? = null
    for (parser in TraceBackParser.PARSERS) {
      val link = parser.findLinkInTrace(line)
      if (link != null && (earliestLink == null || link.startPos < earliestLink.startPos)) {
        earliestLink = link
      }
    }
    return earliestLink
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
