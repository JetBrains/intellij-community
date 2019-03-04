// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.status

import com.intellij.testFramework.TestDataPath
import com.intellij.util.io.readText
import org.jetbrains.idea.svn.api.AbstractSvnClientTest
import org.jetbrains.idea.svn.api.Revision
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.fail

@TestDataPath("\$CONTENT_ROOT/../testData/parse/")
class CmdStatusClientTest : AbstractSvnClientTest() {
  @Test
  fun `parse status`() {
    val actual = parseTestData()

    assertStatus(actual)
    assertNull(actual.changeListName)
  }

  @Test
  fun `parse status in change list`() {
    val actual = parseTestData()

    assertStatus(actual)
    assertEquals("list1", actual.changeListName)
  }

  @Test
  fun `parse status with undefined revision`() {
    val actual = parseTestData()

    assertEquals(File(base, "file1.txt"), actual.file)
    assertEquals(StatusType.STATUS_ADDED, actual.itemStatus)
    assertEquals(StatusType.STATUS_NONE, actual.propertyStatus)
    assertEquals(Revision.UNDEFINED, actual.revision)
  }

  private fun parseTestData(): Status = CmdStatusClient.parseResult(base, getTestData().readText()) ?: fail("Could not parse status")

  private fun assertStatus(actual: Status) {
    assertEquals(File(base, "file1.txt"), actual.file)
    assertEquals(StatusType.STATUS_DELETED, actual.itemStatus)
    assertEquals(StatusType.STATUS_MODIFIED, actual.propertyStatus)
    assertEquals(Revision.of(10), actual.revision)
    assertEquals(true, actual.isWorkingCopyLocked)
    assertEquals(true, actual.isCopied)
    assertEquals(true, actual.isSwitched)
    assertEquals(true, actual.isTreeConflicted)
    assertCommitInfo(actual.commitInfo)
    assertLock(actual.localLock)

    assertEquals(StatusType.STATUS_ADDED, actual.remoteItemStatus)
    assertEquals(StatusType.STATUS_MODIFIED, actual.remotePropertyStatus)
    assertLock(actual.remoteLock)
  }
}