// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.info

import com.intellij.testFramework.TestDataPath
import com.intellij.util.io.readText
import org.jetbrains.idea.svn.SvnUtil.createUrl
import org.jetbrains.idea.svn.api.*
import org.jetbrains.idea.svn.conflict.ConflictAction
import org.jetbrains.idea.svn.conflict.ConflictOperation
import org.jetbrains.idea.svn.conflict.ConflictReason
import org.jetbrains.idea.svn.conflict.ConflictVersion
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.fail

private val REPOSITORY_URL = createUrl("http://abc.com")
private val FILE_URL = REPOSITORY_URL.appendPath("file.txt")
private val FILE1_URL = REPOSITORY_URL.appendPath("file1.txt")
private val FOLDER_URL = REPOSITORY_URL.appendPath("folder")

@TestDataPath("\$CONTENT_ROOT/../testData/parse/")
class CmdInfoClientTest : AbstractSvnClientTest() {
  @Test
  fun `parse info`() {
    val actual = parseTestData()

    assertEquals(File(base, "file1.txt"), actual.file)
    assertEquals(FILE1_URL, actual.url)
    assertEquals(Revision.of(11), actual.revision)
    assertEquals(NodeKind.FILE, actual.nodeKind)
    assertEquals(REPOSITORY_URL, actual.repositoryRootUrl)
    assertEquals("id1", actual.repositoryId)
    assertCommitInfo(actual.commitInfo)
    assertEquals("normal", actual.schedule)
    assertEquals(Depth.INFINITY, actual.depth)
    assertEquals(FILE_URL, actual.copyFromUrl)
    assertEquals(Revision.of(9), actual.copyFromRevision)
    assertLock(actual.lock)
    assertEquals(File(base, "file1.txt.r10"), actual.conflictOldFile)
    assertEquals(File(base, "file1.txt.r11"), actual.conflictNewFile)
    assertEquals(File(base, "file1.txt.mine"), actual.conflictWrkFile)
    assertEquals(File(base, "file1.txt"), actual.treeConflict?.path)
    assertEquals(NodeKind.FILE, actual.treeConflict?.nodeKind)
    assertEquals(ConflictAction.EDIT, actual.treeConflict?.conflictAction)
    assertEquals(ConflictReason.DELETED, actual.treeConflict?.conflictReason)
    assertEquals(ConflictOperation.SWITCH, actual.treeConflict?.operation)
    assert(REPOSITORY_URL, "file1.txt", 9, NodeKind.FILE, actual.treeConflict?.sourceLeftVersion)
    assert(REPOSITORY_URL, "file1.txt", 10, NodeKind.FILE, actual.treeConflict?.sourceRightVersion)
  }

  /* "revision" attribute does not contain long value */
  @Test
  fun `parse info for unversioned`() {
    val actual = parseTestData()

    assertEquals(File(base, "folder"), actual.file)
    assertEquals(FOLDER_URL, actual.url)
    assertEquals(Revision.UNDEFINED, actual.revision)
    assertEquals(NodeKind.DIR, actual.nodeKind)
    assertEquals(REPOSITORY_URL, actual.repositoryRootUrl)
    assertEquals("id1", actual.repositoryId)
    assertEquals(Info.SCHEDULE_ADD, actual.schedule)
    assertEquals(Depth.INFINITY, actual.depth)
  }

  @Suppress("SameParameterValue")
  private fun assert(repositoryRoot: Url, path: String, pegRevision: Long, nodeKind: NodeKind, actual: ConflictVersion?) {
    assertEquals(repositoryRoot, actual?.repositoryRoot)
    assertEquals(path, actual?.path)
    assertEquals(pegRevision, actual?.pegRevision)
    assertEquals(nodeKind, actual?.nodeKind)
  }

  private fun parseTestData() = CmdInfoClient.parseResult(base, getTestData().readText()) ?: fail("Could not parse info")
}