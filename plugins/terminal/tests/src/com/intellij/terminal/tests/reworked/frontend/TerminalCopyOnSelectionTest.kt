package com.intellij.terminal.tests.reworked.frontend

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import com.intellij.psi.PsiDocumentManager
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.terminal.frontend.view.impl.TerminalEditorFactory
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.cancel
import org.jetbrains.plugins.terminal.block.reworked.lang.TerminalOutputPsiFile
import org.jetbrains.plugins.terminal.util.terminalProjectScope
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.awt.Component
import java.awt.KeyboardFocusManager
import java.awt.datatransfer.DataFlavor
import javax.swing.FocusManager

@RunWith(JUnit4::class)
internal class TerminalCopyOnSelectionTest : BasePlatformTestCase() {
  private var defaultFocusManager: KeyboardFocusManager? = null

  override fun setUp() {
    super.setUp()
    defaultFocusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
  }

  override fun tearDown() {
    try {
      KeyboardFocusManager.setCurrentKeyboardFocusManager(defaultFocusManager)
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  @Test
  fun `copy on selection copies block selection from all selected lines`() {
    val editor = createTerminalEditor()
    editor.emitOnTerminal(
      """
      first
      second
      third
      """.trimIndent()
    )

    editor.selectionModel.setBlockSelection(LogicalPosition(0, 1), LogicalPosition(2, 4))

    val transferable = copiedTransferable()
    assertNotNull(transferable)
    assertEquals("irs\neco\nhir", transferable!!.getTransferData(DataFlavor.stringFlavor))
  }

  private fun createTerminalEditor(): Editor {
    val scope = terminalProjectScope(project).childScope("TerminalOutputEditor").also {
      Disposer.register(testRootDisposable) { it.cancel() }
    }
    val editor = TerminalEditorFactory.createOutputEditor(
      project,
      object : JBTerminalSystemSettingsProviderBase() {
        override fun copyOnSelect(): Boolean = true
      },
      scope
    )
    // Copy on selection only works if terminal is the focus owner.
    KeyboardFocusManager.setCurrentKeyboardFocusManager(
      object : FocusManager() {
        override fun getFocusOwner(): Component = editor.contentComponent
      }
    )
    return editor
  }

  private fun Editor.emitOnTerminal(text: String) {
    document.setText(text)
    val psiFile = PsiDocumentManager.getInstance(this@TerminalCopyOnSelectionTest.project).getPsiFile(this.document) as TerminalOutputPsiFile
    psiFile.charsSequence = this.document.immutableCharSequence
  }

  private fun copiedTransferable() =
    CopyPasteManager.getInstance().let { it.systemSelectionContents ?: it.contents }
}
