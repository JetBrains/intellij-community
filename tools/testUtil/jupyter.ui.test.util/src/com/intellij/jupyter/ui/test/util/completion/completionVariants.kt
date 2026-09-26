package com.intellij.jupyter.ui.test.util.completion

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.WaitForException
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.components.elements.JListUiComponent
import com.intellij.driver.sdk.ui.components.elements.popup
import com.intellij.driver.sdk.waitFor
import kotlin.time.Duration.Companion.seconds

fun Driver.checkCompletionVariantsWithReport(expectedVariant: String, reportReference: String? = "reference from previous cell"): IdeaFrameUI = run {
  ideFrame {
    try {
      popup().waitContainsText(expectedVariant, timeout = 15.seconds)
    }
    catch (e: WaitForException) {
      // collecting the failure diagnostics  info.
      // It is better to collect it via api call instead of using the UI TODO @Nikolay.Egorov
      takeScreenshot("check completion")
      //checkScriptingLibraryExists(NOTEBOOK_LIBRARY_PREFIX)
      val target = reportReference ?: expectedVariant.trim().lowercase()
      throw IllegalStateException("Completion variants doesn't contain $target. Base dependencies might be empty", e)
    }
  }
}

fun JListUiComponent.clickOnCompletionVariant(expectedCompletion: String) {
  waitFor(message = "$expectedCompletion not found", timeout = 15.seconds) {
    rawItems.indexOfFirst { it.contains(expectedCompletion) } != -1
  }
  val index = rawItems.indexOfFirst { it.contains(expectedCompletion) }
  clickItemAtIndex(index)
}
