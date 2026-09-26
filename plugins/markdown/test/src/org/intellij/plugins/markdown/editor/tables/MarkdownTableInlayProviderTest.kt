// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor.tables

import com.intellij.codeInsight.hints.InlayHintsSinkImpl
import com.intellij.codeInsight.hints.NoSettings
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.impl.ImaginaryEditor
import com.intellij.psi.PsiFileFactory
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.assertj.core.api.Assertions.assertThat
import org.intellij.plugins.markdown.editor.tables.ui.MarkdownTableInlayProvider
import org.intellij.plugins.markdown.lang.MarkdownFileType
import org.intellij.plugins.markdown.lang.supportsMarkdown
import org.junit.jupiter.api.Test

@TestApplication
internal class MarkdownTableInlayProviderTest {
  private val projectFixture = projectFixture()
  private val project get() = projectFixture.get()

  @Test
  fun `table inlays are not collected for imaginary editors`() = timeoutRunBlocking {
    val source = """
      | header |
      |--------|
      | value  |
    """.trimIndent()
    val file = readAction {
      PsiFileFactory.getInstance(project).createFileFromText(
        "table.md",
        MarkdownFileType.INSTANCE,
        source,
      )
    }
    assertThat(readAction { file.supportsMarkdown() }).isTrue()

    val document = EditorFactory.getInstance().createDocument(source)
    val editor = ImaginaryEditor(project, document)
    val collector = readAction {
      MarkdownTableInlayProvider().getCollectorFor(file, editor, NoSettings(), InlayHintsSinkImpl(editor))
    }

    assertThat(collector).isNull()
  }
}
