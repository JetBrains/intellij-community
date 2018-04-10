// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.mergeinfo

import org.junit.Assert.assertEquals
import org.junit.Test

class MergeRangeListTest {
  @Test
  fun `parse range`() {
    assertRange("10", 10, 10)
    assertRange("10-20", 10, 20)
    assertRange("10*", 10, 10, false)
    assertRange("10-20*", 10, 20, false)
  }

  @Test
  fun `parse merge info`() {
    val mergeInfo = MergeRangeList.parseMergeInfo("""
      /aaa:10,20*,30-40
      /bbb:50-60*
    """.trimIndent())
    val expected = mapOf(
      "/aaa" to MergeRangeList(setOf(MergeRange(10, 10), MergeRange(20, 20, false), MergeRange(30, 40))),
      "/bbb" to MergeRangeList(setOf(MergeRange(50, 60, false)))
    )

    assertEquals(expected, mergeInfo)
  }

  private fun assertRange(value: String, start: Long, endInclusive: Long, isInheritable: Boolean = true) = assertEquals(
    MergeRange(start, endInclusive, isInheritable), MergeRangeList.parseRange(value))
}