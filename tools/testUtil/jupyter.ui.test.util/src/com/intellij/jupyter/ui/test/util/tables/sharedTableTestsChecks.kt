package com.intellij.jupyter.ui.test.util.tables

import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.components.elements.popup
import com.intellij.driver.sdk.ui.components.notebooks.NotebookEditorUiComponent
import com.intellij.driver.sdk.ui.getClipboardText
import com.intellij.driver.sdk.ui.should
import com.intellij.driver.sdk.waitFor
import io.kotest.matchers.shouldBe

// Shared tables test utilities used across Kotlin and Python notebooks to avoid duplication
fun NotebookEditorUiComponent.checkTableSize() {
  firstTable.should { hasText("3 rows × 5 cols") }
}

fun NotebookEditorUiComponent.checkTablePaging() {
  firstTable.run {
    changePageSizeTo(2)

    tableView.should { rowCount() == 2 }

    goNextPage()
    tableView.rowCount() shouldBe 1

    goPreviousPage()
    tableView.rowCount() shouldBe 2
  }
}

fun NotebookEditorUiComponent.checkTableCellContextMenuActions(column: Int) {
  lastTable.run {
    tableView.rightClickCell(0, column)

    driver.ideFrame {
      popup().run {
        waitOneText("Copy").strictClick()
      }
    }

    waitFor { driver.getClipboardText() == "Daniel" }
  }
}