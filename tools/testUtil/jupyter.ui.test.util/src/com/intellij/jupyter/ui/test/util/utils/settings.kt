package com.intellij.jupyter.ui.test.util.utils

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.components.elements.checkBox
import com.intellij.driver.sdk.ui.components.elements.dialog
import com.intellij.driver.sdk.ui.components.elements.isDialogOpened
import com.intellij.driver.sdk.ui.components.elements.popup
import com.intellij.driver.sdk.ui.pasteText
import com.intellij.driver.sdk.ui.ui
import com.intellij.driver.sdk.waitFor
import java.awt.event.KeyEvent

/*
startOption: IPyKernel, JupyterLab
 */
fun Driver.jupyterServersStartOption(startOption: String) {
  step("Select $startOption start option") {
    repeat(2) {
      val result = runCatching {
        ideFrame {
          openSettings("Jupyter Servers")

          val autoDetectCheckBox = checkBox { byAccessibleName("Autodetected execution mode") }
          waitFor { autoDetectCheckBox.present() }
          autoDetectCheckBox.uncheck()

          val optionButton = x { byAccessibleName(startOption) }
          waitFor { optionButton.present() }
          optionButton.click()

          ui.x("//div[@text='Command line arguments:']/following-sibling:: *[@class='ExpandableTextField' or @javaclass='com.intellij.ui.components.fields.ExpandableTextField'][1]").click()
          keyboard {
            key(KeyEvent.VK_END)
            driver.ui.pasteText(" --debug")
          }

          x { byAccessibleName("OK") }.click()
        }
      }

      if (result.isSuccess) return@step

      tryCloseSettingsIfOpen()
      result.exceptionOrNull()?.let { last -> if (it == 1) throw last }
    }
  }
}

fun Driver.openSettings(name: String) {
  ideFrame {
    val settingsButton = x { byAccessibleName("IDE and Project Settings") }
    waitFor { settingsButton.present() }
    settingsButton.strictClick()

    waitFor("Popup should present") { popup().present() }
    popup().run { waitOneText("Settings…").click() }

    val searchButton = x { byAccessibleName("Search") }
    waitFor { searchButton.present() }
    searchButton.click()
    keyboard { driver.ui.pasteText(name) }
  }
}

private fun Driver.tryCloseSettingsIfOpen() {
  ideFrame {
    if (isDialogOpened()) {
      dialog().closeDialog()
    }
  }
}
