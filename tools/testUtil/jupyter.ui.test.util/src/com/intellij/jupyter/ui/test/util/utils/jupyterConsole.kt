package com.intellij.jupyter.ui.test.util.utils

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.waitFor
import kotlin.time.Duration.Companion.seconds

fun Driver.getJupyterConsoleButton(): UiComponent {
  return ideFrame().x("//div[@myicon='consoleJupyter.svg']")
}

fun Driver.jupyterConsolePanel(): UiComponent {
  return ideFrame().x("//div[@class='JupyterConsolePanel']")
}

fun Driver.closeJupyterConsole() {
  ideFrame {
    if (jupyterConsolePanel().present()) {
      x("//div[@class='SquareStripeButton' and @accessiblename='Jupyter']").click()
    }
  }
}

fun Driver.focusJupyterConsoleCommandArea() {
  val xPath = jupyterConsolePanel().xx("//div[@class='JPanel']//div[@class='JBViewport']//div[@class='EditorComponentImpl']")
  waitFor {
    xPath.list().isNotEmpty()
  }
  xPath.list().last().click()
}

fun Driver.jupyterConsole() = ideFrame {
  waitFor(timeout = 15.seconds, message = "Jupyter Console button is not present") {
    getJupyterConsoleButton().present()
  }
  getJupyterConsoleButton().click()
  waitFor(timeout = 15.seconds) {
    jupyterConsolePanel().present()
  }
}

fun Driver.jupyterConsoleVariablesPanel(): UiComponent {
  return jupyterConsolePanel().x("//div[@class='PythonJupyterVariableViewPanel']")
}

/*
  Use this function to search only for names (not varName = ..., just varName)
 */
fun Driver.jupyterConsoleVariablesPanelGetFirstOccurence(name: String): Int {
  return jupyterConsoleVariablesPanel().getAllTexts().indexOfFirst { it.text.contains(name) }
}

fun Driver.jupyterConsoleVariablesPanelGetFirstOccurence(names: List<String>): List<Int> {
  val text = jupyterVariablesPanel().getAllTexts()
  return names.map { name -> text.indexOfFirst { it.text.contains(name) } }
}
