package com.intellij.terminal.tests.reworked.frontend

import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import com.intellij.psi.PsiDocumentManager
import com.intellij.terminal.frontend.view.impl.TerminalEditorFactory
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.cancel
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.JBTerminalSystemSettingsProvider
import org.jetbrains.plugins.terminal.util.terminalProjectScope
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
internal class TerminalOutputPsiFileTest : BasePlatformTestCase() {
  @Test
  fun `check document is not detected as uncommitted after change`() {
    val scope = terminalProjectScope(project).childScope("TerminalOutputEditor")
    Disposer.register(testRootDisposable) { scope.cancel() }
    val editor = TerminalEditorFactory.createOutputEditor(project, JBTerminalSystemSettingsProvider(), scope)
    val document = editor.document
    document.setText("123")

    document.insertString(3, "456")

    assertThat(PsiDocumentManager.getInstance(project).isUncommited(document)).isFalse()
  }
}