package com.intellij.terminal.tests.reworked.frontend

import com.intellij.psi.PsiDocumentManager
import com.intellij.terminal.frontend.TerminalEditorFactory
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.JBTerminalSystemSettingsProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
internal class TerminalOutputPsiFileTest : BasePlatformTestCase() {
  @Test
  fun `check document is not detected as uncommitted after change`() {
    val editor = TerminalEditorFactory.createOutputEditor(project, JBTerminalSystemSettingsProvider(), testRootDisposable)
    val document = editor.document
    document.setText("123")

    document.insertString(3, "456")

    assertThat(PsiDocumentManager.getInstance(project).isUncommited(document)).isFalse()
  }
}