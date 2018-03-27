// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.mergeinfo

import org.jetbrains.idea.svn.api.ErrorCode.MERGE_INFO_PARSE_ERROR
import org.jetbrains.idea.svn.commandLine.SvnBindException

data class MergeRangeList(val ranges: Set<MergeRange>) {
  companion object {
    @JvmStatic
    @Throws(SvnBindException::class)
    fun parseMergeInfo(value: String) = value.lineSequence().map { parseLine(it) }.toMap()

    @Throws(SvnBindException::class)
    fun parseRange(value: String): MergeRange {
      val revisions = value.removeSuffix("*").split('-')
      if (revisions.isEmpty() || revisions.size > 2) throwParseFailed(value)

      val start = parseRevision(revisions[0])
      val end = if (revisions.size == 2) parseRevision(revisions[1]) else start
      return MergeRange(start, end, value.lastOrNull() != '*')
    }

    private fun parseLine(value: String): Pair<String, MergeRangeList> {
      val parts = value.split(':')
      if (parts.size != 2) throwParseFailed(value)

      return Pair(parts[0], MergeRangeList(parts[1].splitToSequence(',').map { parseRange(it) }.toSet()))
    }

    private fun parseRevision(value: String) = try {
      value.toLong()
    }
    catch (e: NumberFormatException) {
      throwParseFailed(value)
    }

    private fun throwParseFailed(value: String): Nothing = throw SvnBindException(MERGE_INFO_PARSE_ERROR, "Could not parse $value")
  }
}