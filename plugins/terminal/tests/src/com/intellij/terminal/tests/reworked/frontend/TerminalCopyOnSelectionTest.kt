package com.intellij.terminal.tests.reworked.frontend

import com.intellij.mock.MockFocusManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.terminal.frontend.view.impl.TerminalEditorFactory
import com.intellij.terminal.tests.reworked.util.TerminalTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.cancel
import org.jetbrains.plugins.terminal.util.terminalProjectScope
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.condition.OS
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.awt.datatransfer.DataFlavor

@RunWith(JUnit4::class)
internal class TerminalCopyOnSelectionTest : BasePlatformTestCase() {
  @Before
  fun requireNotLinux() {
    Assume.assumeTrue("Can't run this test on Linux because non-headless mode is required to access system selection content", OS.current() != OS.LINUX)
  }

  @Test
  fun `copy on selection works for single selection and does not clear clipboard when selection becomes empty`() {
    val editor = createTerminalEditor()
    editor.document.setText(
      """
      first
      second
      third
      """.trimIndent()
    )

    // Select "first"
    editor.selectionModel.setSelection(0, 5)
    val copied = copiedTransferable()
    assertNotNull(copied)
    assertEquals("first", copied!!.getTransferData(DataFlavor.stringFlavor))

    // Clearing the selection fires a selection change event with an empty selection.
    editor.selectionModel.removeSelection()
    val afterClear = copiedTransferable()
    assertNotNull(afterClear)
    assertEquals("first", afterClear!!.getTransferData(DataFlavor.stringFlavor))
  }

  @Test
  fun `copy on selection works for block selection and does not clear clipboard when block selection is cleared`() {
    val editor = createTerminalEditor()
    editor.document.setText(
      """
      first
      second
      third
      """.trimIndent()
    )

    // A vertical (block) selection is copied to the clipboard.
    editor.selectionModel.setBlockSelection(LogicalPosition(0, 1), LogicalPosition(2, 4))
    val copied = copiedTransferable()
    assertNotNull(copied)
    assertEquals("irs\neco\nhir", copied!!.getTransferData(DataFlavor.stringFlavor))

    // Clearing the block selection collapses back to a single caret (as a click does) and fires a
    // selection change event with an empty selection. It must not overwrite the clipboard.
    editor.caretModel.removeSecondaryCarets()
    editor.selectionModel.removeSelection(true)
    val afterClear = copiedTransferable()
    assertNotNull(afterClear)
    assertEquals("irs\neco\nhir", afterClear!!.getTransferData(DataFlavor.stringFlavor))
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
    TerminalTestUtil.replaceKeyboardFocusManager(
      testRootDisposable,
      MockFocusManager(editor.contentComponent)
    )
    return editor
  }

  private fun copiedTransferable() = CopyPasteManager.getInstance().let { it.systemSelectionContents ?: it.contents }
}
