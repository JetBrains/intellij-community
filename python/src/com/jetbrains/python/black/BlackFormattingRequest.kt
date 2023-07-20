// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.black

import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.python.psi.PyIndentUtil

sealed class BlackFormattingRequest {
  abstract val fragmentToFormat: String
  abstract val virtualFile: VirtualFile

  class Fragment(val fragment: String, override val virtualFile: VirtualFile) : BlackFormattingRequest() {
    private val extractedIndent: String
    private val whitespaceBefore: String
    private val whitespaceAfter: String
    private val endsWithNewLine: Boolean

    override val fragmentToFormat: String

    init {
      val firstNotEmptyLine = fragment.lines().first { it.isNotBlank() }

      extractedIndent = PyIndentUtil.getLineIndent(firstNotEmptyLine)
      whitespaceBefore = fragment.takeWhile { it.isWhitespace() }
      whitespaceAfter = fragment.takeLastWhile { it.isWhitespace() }
      endsWithNewLine = fragment.endsWith("\n")

      fragmentToFormat = PyIndentUtil.removeCommonIndent(fragment, false)
    }

    fun postProcessResponse(response: String): String {
      return buildString {
        val lines = response.trimEnd().lines()

        if (!response.contains('\n')) {
          append(extractedIndent)
          append(response)
          if (endsWithNewLine) {
            append('\n')
          }
          return@buildString
        }

        append(whitespaceBefore)
        append(lines.first())
        for (line in lines.listIterator(1)) {
          appendLine()
          append(line.prependIndent(extractedIndent))
        }
        append(whitespaceAfter)
      }
    }
  }

  class File(override val fragmentToFormat: String, override val virtualFile: VirtualFile) : BlackFormattingRequest()
}