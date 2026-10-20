// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.structureView

import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.changes.StructureChangeCollector
import com.intellij.psi.PsiFileFactory
import com.intellij.testFramework.IndexingTestUtil.Companion.suspendUntilIndexesAreReady
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

@TestApplication
internal class XmlStructureChangeCollectorTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  @Test
  fun `ranges include the complete current tag`(): Unit = timeoutRunBlocking {
    val before = """
      <root>
        <changed value="before"><stable/></changed>
        <removed/>
      </root>
    """.trimIndent()
    val after = """
      <root>
        <changed value="after"><stable/></changed>
        <added/>
      </root>
    """.trimIndent()
    val project = projectFixture.get()
    suspendUntilIndexesAreReady(project)
    val root = smartReadAction(project) {
      val factory = PsiFileFactory.getInstance(project)
      val beforeFile = factory.createFileFromText("sample.xml", XmlFileType.INSTANCE, before)
      val afterFile = factory.createFileFromText("sample.xml", XmlFileType.INSTANCE, after)
      StructureChangeCollector.collect(beforeFile, afterFile).single()
    }

    assertEquals(FileStatus.NOT_CHANGED, root.status)
    assertEquals(TextRange(0, after.length), root.range)
    val changed = root.children.single { it.status == FileStatus.MODIFIED }
    assertEquals(sourceRange(after, "<changed value=\"after\"><stable/></changed>"), changed.range)
    val added = root.children.single { it.status == FileStatus.ADDED }
    assertEquals(sourceRange(after, "<added/>"), added.range)
    assertNull(root.children.single { it.status == FileStatus.DELETED }.range)
  }

  private fun sourceRange(text: String, elementText: String): TextRange {
    val offset = text.indexOf(elementText)
    require(offset >= 0)
    return TextRange(offset, offset + elementText.length)
  }
}
