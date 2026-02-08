package com.intellij.lambda.testFramework.testApi

import com.intellij.openapi.ui.popup.StackingPopupDispatcher
import com.intellij.remoteDev.tests.impl.utils.waitSuspending
import com.intellij.ui.TitlePanel
import com.intellij.ui.popup.AbstractPopup
import com.intellij.ui.popup.StackingPopupDispatcherImpl
import com.intellij.ui.popup.list.ListPopupImpl
import com.intellij.util.ui.UIUtil
import javax.swing.text.JTextComponent
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Also see ScriptingAPI.Hints.kt, ScriptingAPI.Balloons.kt
 */

suspend inline fun <reified T : AbstractPopup> waitTillPopUpOpened(expectedTitle: String? = null, timeout: Duration = 20.seconds): T =
  waitSuspending(
    "Popup with class ${T::class.simpleName} ${expectedTitle?.let { "with title '$it' " }.orEmpty()}is opened",
    timeout,
    getter = { openedPopupOrNull?.let { it as? T } },
    checker = { it != null && (expectedTitle == null || it.titleText == expectedTitle) },
    failMessageProducer = {
      if (it == null) "No popup was opened"
      else "Popup with '${it.titleText}' was opened"
    }
  )!!

suspend fun waitTillPopUpOpened(timeout: Duration = 20.seconds, filter: AbstractPopup.() -> Boolean): AbstractPopup =
  waitSuspending("Popup matching filter is opened",
                                                                          timeout,
                                                                          getter = { openedPopupOrNull },
                                                                          checker = { it != null && it.filter() },
                                                                          failMessageProducer = {
                                                                            if (it == null) "No popup was opened"
                                                                            else "Popup with '${it.titleText}' was opened"
                                                                          }
  )!!

suspend fun waitTillNoPopUpOpened(timeout: Duration = 20.seconds) =
  waitSuspending("Popup is not opened", timeout) {
    openedPopupOrNull == null
  }

fun isPopupOpened() =
  openedPopupOrNull != null


val openedPopupOrNull: AbstractPopup?
  get() {
    val dispatcherImpl = StackingPopupDispatcher.getInstance() as? StackingPopupDispatcherImpl
    return (dispatcherImpl?.focusedPopup ?: dispatcherImpl?.findPopup()) as? AbstractPopup
  }

val openedPopup: AbstractPopup
  get() = openedPopupOrNull ?: error("No opened popup")

val AbstractPopup.titleText: String?
  get() = (title as? TitlePanel)?.label?.text // if title is not TitlePanel, it means the tittle wasn't set

val AbstractPopup.text: String?
  get() = UIUtil.findComponentOfType(content, JTextComponent::class.java)?.text


fun ListPopupImpl.getListPopupItems(): List<Any> {
  val aModel = list.model
  return (0..aModel.size).mapNotNull { elementIndex ->
    aModel.getElementAt(elementIndex)
  }
}

