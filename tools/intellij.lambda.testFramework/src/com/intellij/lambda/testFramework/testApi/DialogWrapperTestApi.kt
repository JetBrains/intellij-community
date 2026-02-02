@file:OptIn(FlowPreview::class)

package com.intellij.lambda.testFramework.testApi

import com.intellij.lambda.testFramework.frameworkLogger
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.remoteDev.tests.impl.utils.waitSuspending
import com.intellij.ui.ComponentUtil
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withTimeout
import java.awt.KeyboardFocusManager
import java.awt.Window
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

suspend fun waitForDialogWrapper(expectedTitle: String, timeout: Duration = 15.seconds): DialogWrapper {
  return waitForDialogWrapper(timeout) { it.title == expectedTitle }
}

suspend fun waitForDialogWrapper(timeout: Duration = 15.seconds, filter: ((DialogWrapper) -> Boolean) = { true }): DialogWrapper {
  return withTimeout(timeout) { activeDialogsFlow().filter { it.isShowing && filter(it) }.first() }
}

/**
 * Provides a flow of windows. It emits the current active windows into the flow immediately,
 * then it listens for active window change via KeyboardFocusManager and emits every given value.
 * It may emit null as well.
 *
 * Remember to close the flow consumption (use terminal operators) to make it unsubscribed from KeyboardFocusManager
 */
fun activeWindowsFlow(): Flow<Window?> {
  return callbackFlow {
    val startingActiveWindow = ComponentUtil.getActiveWindow()
    send(startingActiveWindow)
    frameworkLogger.info("Starting active window: ${startingActiveWindow}")
    val listener = object : PropertyChangeListener {
      override fun propertyChange(evt: PropertyChangeEvent) {
        frameworkLogger.info("Active window changed: ${evt.newValue}")
        trySend(evt.newValue as? Window)
      }
    }
    val focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
    focusManager.addPropertyChangeListener("activeWindow", listener)
    awaitClose {
      focusManager.removePropertyChangeListener("activeWindow", listener)
    }
  }
}

/**
 * The same as [activeWindowsFlow] but filters only dialogs using [DialogWrapper.findInstance]
 */
fun activeDialogsFlow(): Flow<DialogWrapper> {
  return activeWindowsFlow().filterNotNull().mapNotNull { DialogWrapper.findInstance(it) }
}

suspend fun DialogWrapper.closeDialog(closingAction: suspend DialogWrapper.() -> Unit) {
  closingAction()
  waitClosed()
}

suspend fun DialogWrapper.waitClosed() {
  waitSuspending("Dialog is closed", 15.seconds) {
    isDisposed
  }
}

val openedDialogWrappers: List<DialogWrapper>
  get() = Window.getWindows().mapNotNull { DialogWrapper.findInstance(it) }