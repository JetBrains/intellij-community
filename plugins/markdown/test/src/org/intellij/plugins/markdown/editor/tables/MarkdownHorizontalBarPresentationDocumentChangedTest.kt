// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.impl.PsiDocumentManagerBase
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import org.intellij.plugins.markdown.editor.tables.ui.presentation.HorizontalBarPresentation
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTable
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
@Suppress("MarkdownIncorrectTableFormatting")
class MarkdownHorizontalBarPresentationDocumentChangedTest : LightPlatformCodeInsightTestCase() {
  // language=Markdown
  private val source = """
    | 名字 | 年龄 |
    |------|------|
    | 投放 | 3    |
  """.trimIndent()

  @Test
  fun `documentChanged in HorizontalBarPresentation must not synchronously schedule commit actions`() {
    configureFromFileText("test.md", source)

    val pdm = PsiDocumentManager.getInstance(project) as PsiDocumentManagerBase
    val field = PsiDocumentManagerBase::class.java.getDeclaredField("documentCommitActions")
    field.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    val commitActions = field.get(pdm) as MutableMap<Any, MutableList<*>>

    fun insertSpaceAndCountPendingCommitActions(): Int {
      pdm.commitAllDocuments()
      commitActions.remove(editor.document)
      WriteCommandAction.runWriteCommandAction(project) {
        editor.document.insertString(0, " ")
      }
      return commitActions[editor.document]?.size ?: 0
    }

    val baseline = insertSpaceAndCountPendingCommitActions()

    WriteCommandAction.runWriteCommandAction(project) {
      editor.document.setText(source)
    }
    val table = PsiTreeUtil.findChildOfType(file, MarkdownTable::class.java)
                ?: error("Expected a MarkdownTable in the fixture")
    HorizontalBarPresentation(editor, table)

    val withListener = insertSpaceAndCountPendingCommitActions()

    assertEquals(
      "HorizontalBarPresentation must not synchronously schedule commit actions from " +
      "documentChanged — doing so during Undo replay freezes the IDE.",
      baseline,
      withListener
    )
  }
}
