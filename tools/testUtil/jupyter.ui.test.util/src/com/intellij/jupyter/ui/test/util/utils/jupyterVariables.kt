package com.intellij.jupyter.ui.test.util.utils

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.ui.UiText
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.components.elements.actionButton
import com.intellij.driver.sdk.ui.components.elements.popup
import com.intellij.driver.sdk.waitFor
import kotlin.time.Duration.Companion.seconds

fun Driver.getJupyterVariablesButton(): UiComponent {
  return ideFrame().x("//div[@class='SquareStripeButton' and @accessiblename='Jupyter Variables']")
}

fun Driver.jupyterVariablesPanel(): UiComponent {
  return ideFrame().x("//div[@class='PythonJupyterVarsToolWindow']")
}

fun Driver.jupyterVariables() = ideFrame {
  if (jupyterVariablesPanel().present()) return@ideFrame
  getJupyterVariablesButton().click()
  waitFor(timeout = 15.seconds, message = "Jupyter Variables should be open") {
    jupyterVariablesPanel().present()
  }
}

fun Driver.refreshVariables() = ideFrame {
  getJupyterVariablesButton().moveMouse() // move mouse to ensure there is no unexpected popup due to small notebook editor

  jupyterVariablesPanel().run {
    val refreshButton = actionButton { byAccessibleName("Refresh Variables") }
    waitFor { refreshButton.present() }
    refreshButton.click()
  }
}

fun Driver.sortJupyterVariableBy(param: String) = ideFrame {
  getJupyterVariablesButton().moveMouse() // move mouse to ensure there is no unexpected popup due to small notebook editor
  jupyterVariablesPanel().run {
    val sortButton = actionButton { byAccessibleName("Sort By") }
    waitFor { sortButton.present() }
    sortButton.click()
  }
  popup().run {
    waitOneText(param).click()
  }
  jupyterVariablesPanel().click()
}

fun Driver.jupyterVariablesPanelContains(name: String): Boolean {
  return jupyterVariablesPanel().getAllTexts { it.text.contains(name) }.isNotEmpty()
}

/*
  Use this function to search only for names (not varName = ..., just varName)
 */
fun Driver.jupyterVariablesPanelGetFirstOccurence(name: String): Int {
  return jupyterVariablesPanel().getAllTexts().indexOfFirst { it.text.contains(name) }
}

fun Driver.jupyterVariablesPanelGetFirstOccurence(names: List<String>): List<Int> {
  val text = jupyterVariablesPanel().getAllTexts()
  return names.map { name -> text.indexOfFirst { it.text.contains(name) } }
}

fun Driver.jupyterVariablesPanelGetText(text: String): UiText {
  return jupyterVariablesPanel().waitOneText(text)
}
