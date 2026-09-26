// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.api

import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.vcs.AbstractJunitVcsTestCase
import org.jetbrains.idea.svn.SvnTestCase.getPluginHome
import org.jetbrains.idea.svn.SvnUtil.parseDate
import org.jetbrains.idea.svn.checkin.CommitInfo
import org.jetbrains.idea.svn.lock.Lock
import org.junit.Before
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.assertEquals

abstract class AbstractSvnClientTest : AbstractJunitVcsTestCase() {
  lateinit var base: File

  @Before
  fun setUp() {
    base = File(testName)
  }

  fun getTestData(): Path {
    val fileName = PlatformTestUtil.getTestName(testName.removePrefix("parse").trim(), true)
    return Paths.get(getPluginHome(), "testData", "parse", "$fileName.xml")
  }

  protected fun assertCommitInfo(commitInfo: CommitInfo) {
    assertEquals(9, commitInfo.revisionNumber)
    assertEquals("author1", commitInfo.author)
    assertEquals(COMMIT_DATE, commitInfo.date)
  }

  protected fun assertLock(actual: Lock?) {
    assertEquals("comment1", actual?.comment)
    assertEquals("author1", actual?.owner)
    assertEquals(LOCK_DATE, actual?.creationDate)
  }

  companion object {
    val COMMIT_DATE = parseDate("2018-01-01T12:00:00.000000Z")
    val LOCK_DATE = parseDate("2018-01-01T15:00:00.000000Z")
  }
}