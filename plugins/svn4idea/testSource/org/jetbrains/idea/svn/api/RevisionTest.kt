// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn.api

import org.jetbrains.idea.svn.api.Revision.Companion.BASE
import org.jetbrains.idea.svn.api.Revision.Companion.COMMITTED
import org.jetbrains.idea.svn.api.Revision.Companion.HEAD
import org.jetbrains.idea.svn.api.Revision.Companion.PREV
import org.jetbrains.idea.svn.api.Revision.Companion.UNDEFINED
import org.jetbrains.idea.svn.api.Revision.Companion.WORKING
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.*

class RevisionTest {
  @Test
  fun `parse keyword`() {
    assertParse(BASE, "BASE")
    assertParse(COMMITTED, "COMMITTED")
    assertParse(HEAD, "HEAD")
    assertParse(PREV, "PREV")
    assertParse(WORKING, "WORKING")
  }

  @Test
  fun `parse number`() {
    assertParse(Revision.of(10), "10")
    assertParse(UNDEFINED, "-10")
    assertParse(Revision.of(0), "0")
    assertParse(UNDEFINED, "-1")
  }

  @Test
  fun `parse date`() {
    val utc = LocalDateTime.of(2017, 8, 26, 13, 31, 46, 60233000).atZone(ZoneOffset.UTC)
    val date = Date.from(utc.toInstant())

    assertDate(date, "2017-08-26T13:31:46.060233Z")
    assertDate(date, "{2017-08-26T13:31:46.060233Z}")
    assertDate(date, DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(utc))
  }

  private fun assertDate(date: Date, value: String) = assertParse(Revision.of(date), value)
  private fun assertParse(expected: Revision, value: String) = assertEquals(expected, Revision.parse(value))
}
