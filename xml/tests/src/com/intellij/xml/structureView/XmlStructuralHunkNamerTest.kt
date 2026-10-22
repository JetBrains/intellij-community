// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.structureView

import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.changes.HunkLines
import com.intellij.openapi.vcs.changes.StructuralHunkNamer
import com.intellij.psi.PsiFileFactory
import com.intellij.testFramework.IndexingTestUtil.Companion.suspendUntilIndexesAreReady
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@TestApplication
internal class XmlStructuralHunkNamerTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  @Test
  fun `one-line tags show their own lines and the root stays out`(): Unit = timeoutRunBlocking {
    val before = "<root>\n  <changed value=\"before\"><stable/></changed>\n  <removed/>\n</root>"
    val after = "<root>\n  <changed value=\"after\"><stable/></changed>\n  <added/>\n</root>"
    val project = projectFixture.get()
    suspendUntilIndexesAreReady(project)
    val name = smartReadAction(project) {
      val factory = PsiFileFactory.getInstance(project)
      val beforeFile = factory.createFileFromText("sample.xml", XmlFileType.INSTANCE, before)
      val afterFile = factory.createFileFromText("sample.xml", XmlFileType.INSTANCE, after)
      StructuralHunkNamer.name(beforeFile, afterFile, listOf(HunkLines(1, 3, 1, 3))).single()
    }

    assertEquals(listOf("<changed value=\"after\"><stable/></changed>" to FileStatus.MODIFIED, "<added/>" to FileStatus.ADDED,
                        "<removed/>" to FileStatus.DELETED),
                 name.parts.map { it.text to it.status })
  }
}
