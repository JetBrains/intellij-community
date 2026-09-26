package com.intellij.jupyter.ui.test.util.codeinsight

import com.intellij.driver.sdk.IdeTheme
import com.intellij.driver.sdk.ui.components.notebooks.NotebookEditorUiComponent
import com.intellij.driver.sdk.ui.remote.toHexString
import io.kotest.matchers.shouldBe

fun NotebookEditorUiComponent.checkLookAndFeel(ideTheme: IdeTheme) {

  val (expectedBackgroundColor, expectedToolbarColor) = when (ideTheme) {
    IdeTheme.LIGHT -> "#F7F8FA" to "#FFFFFF"
    IdeTheme.DARK -> "#2B2D30" to "#1E1F22"
  }

  toolbar.component.getBackground().toHexString() shouldBe expectedToolbarColor
  notebookCellEditors.first().component.getBackground().toHexString() shouldBe expectedBackgroundColor
}
