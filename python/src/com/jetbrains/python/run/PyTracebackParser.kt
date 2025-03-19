// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run

import com.jetbrains.python.traceBackParsers.LinkInTrace
import com.jetbrains.python.traceBackParsers.TraceBackParserAdapter
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Finds links in default python traceback
 *
 * @author Ilya.Kazakevich
 */
open class PyTracebackParser : TraceBackParserAdapter(Pattern.compile(
  "(File \"(?<file>[^0-9][^\"]{0,200})\", line (?<line>\\d{1,8}))|(File (?<file2>[^0-9][^\"]{0,200}):(?<line2>\\d{1,8}))")) {
  override fun findLinkInTrace(line: String, matchedMatcher: Matcher): LinkInTrace {
    val file1 = matchedMatcher.group("file")
    val file2 = matchedMatcher.group("file2")
    val fileName = (file1 ?: file2).replace('\\', '/')

    val lineNumber1 = matchedMatcher.group("line")
    val lineNumber2 = matchedMatcher.group("line2")
    val lineNumber = (lineNumber1 ?: lineNumber2).toInt()

    if (file1 != null && lineNumber1 != null) {
      val startPos = line.indexOf('\"') + 1
      val endPos = line.indexOf('\"', startPos)
      return LinkInTrace(fileName, lineNumber, startPos, endPos)
    }
    else {
      val startPos = matchedMatcher.start("file2")
      val endPos = matchedMatcher.end("line2")
      return LinkInTrace(fileName, lineNumber, startPos, endPos)
    }
  }
}