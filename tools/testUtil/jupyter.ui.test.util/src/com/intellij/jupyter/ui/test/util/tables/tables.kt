package com.intellij.jupyter.ui.test.util.tables

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.components.elements.JComboBoxUiComponent
import com.intellij.driver.sdk.ui.components.elements.NotebookTableOutputUi
import com.intellij.driver.sdk.ui.components.elements.actionButton
import com.intellij.driver.sdk.ui.components.notebooks.NotebookEditorUiComponent
import com.intellij.driver.sdk.ui.pasteText
import com.intellij.driver.sdk.ui.ui
import com.intellij.driver.sdk.waitFor
import com.intellij.driver.sdk.ui.components.elements.popups
import com.intellij.jupyter.ui.test.util.utils.waitNotEmpty

val NotebookEditorUiComponent.firstTable: NotebookTableOutputUi get() = waitForNonEmptyTables().first()
val NotebookEditorUiComponent.lastTable: NotebookTableOutputUi get() = waitForNonEmptyTables().last()

fun NotebookEditorUiComponent.waitForNonEmptyTables(): List<NotebookTableOutputUi> = (::notebookTables).waitNotEmpty()

fun IdeaFrameUI.getFilterViewPanel(): UiComponent {
  return x("//div[@class='HeavyWeightWindow']//div[@class='MyContentPanel']")
}

/*
  Available comparators: [is None, is not None, <, ≤, >, ≥, =, ≠, in, not in, contains, starts with, ends with]
 */
fun Driver.applyFilter(param: String? = null, comparator: String? = null, logicalOperator: String? = null, value: String? = null, first: Boolean = false) {
  ideFrame {
    actionButton { byAccessibleName("Open Filter View") }.strictClick()
    waitFor {
      getFilterViewPanel().present()
    }
    var comboBoxes: List<JComboBoxUiComponent>

    getFilterViewPanel().run {
      if (!first) {
        x { byAccessibleName("Add filter") }.click()
      }
      comboBoxes = xx(JComboBoxUiComponent::class.java) { byClass("ComboBox") }.list()

      comboBoxes.last().click()
      comparator?.let { comboBoxes.last().selectItem(it) }

      comboBoxes[comboBoxes.lastIndex - 1].moveMouse()
      param?.let { comboBoxes[comboBoxes.lastIndex - 1].selectItem(it) }
    }

    if (!first && logicalOperator != null) {
      comboBoxes[comboBoxes.lastIndex - 2].click()
      waitFor { popups().list().last().hasText(logicalOperator) }
      popups().list().last().waitOneText(logicalOperator).click()
    }

    getFilterViewPanel().run {
      xx { byAccessibleName("Editor") }.list().last().strictClick()
      value?.let {
        keyboard {
          ui.pasteText(it)
        }
      }
      actionButton { byAccessibleName("Apply") }.click()
    }
  }
}

fun Driver.removeFilter(index: Int = 0) {
  ideFrame {
    actionButton { byAccessibleName("Open Filter View") }.strictClick()
    waitFor {
      getFilterViewPanel().present()
    }

    getFilterViewPanel().run {
      xx { byAccessibleName("Additional Filter Actions") }.list()[index].click()
    }

    popups().list().last().waitOneText("Remove Filter").click()

    getFilterViewPanel().run {
      actionButton { byAccessibleName("Apply") }.click()
    }
  }
}

fun Driver.removeAllFilters() {
  ideFrame {
    actionButton { byAccessibleName("Open Filter View") }.strictClick()
    waitFor {
      getFilterViewPanel().present()
    }

    getFilterViewPanel().actionButton { byAccessibleName("Delete") }.click()
  }
}
