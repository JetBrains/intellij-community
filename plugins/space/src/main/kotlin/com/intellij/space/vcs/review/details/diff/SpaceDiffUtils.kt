// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review.details

import circlet.client.api.FileContent
import circlet.client.api.LineEnding
import com.intellij.util.LineSeparator

internal fun getLineEnding(lineEnding: LineEnding): String = when (lineEnding) {
  LineEnding.CR -> LineSeparator.CR
  LineEnding.LF -> LineSeparator.LF
  LineEnding.CRLF -> LineSeparator.CRLF
}.separatorString

internal fun getFileContent(fileContent: FileContent?): String {
  if (fileContent?.lineEnding != null) {
    val separator = getLineEnding(fileContent.lineEnding!!)
    return fileContent.lines.joinToString(separator) { line -> line.text }
  }
  return ""
}