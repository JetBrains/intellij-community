package com.intellij.lambda.testFramework.testApi

import com.intellij.lambda.testFramework.frameworkLogger
import com.intellij.lambda.testFramework.testApi.utils.tryTimes
import com.intellij.openapi.ui.isFocusAncestor
import com.intellij.openapi.wm.ToolWindow
import com.intellij.remoteDev.tests.LambdaIdeContext
import com.intellij.remoteDev.tests.impl.utils.waitSuspending
import java.awt.KeyboardFocusManager
import javax.swing.JComponent
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

context(lambdaIdeContext: LambdaIdeContext)
suspend fun JComponent.waitForFocus(componentName: String, timeout: Duration = 10.seconds) {
  waitSuspending("Focus for ${(componentName)} is received", timeout,
                                                                          checker = { isFocusAncestor() },
                                                                          failMessageProducer = {
                                                                            val keyboardFocusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()

                                                                            "This component: $this" +
                                                                            "\n isFocusOwner=" + isFocusOwner + " isFocusAncestor=" + isFocusAncestor() +

                                                                            "\nActual focused component: " +
                                                                            "\nfocusedWindow is " + keyboardFocusManager.focusedWindow +
                                                                            "\nfocusOwner is " + keyboardFocusManager.focusOwner +
                                                                            "\nactiveWindow is " + keyboardFocusManager.activeWindow +
                                                                            "\npermanentFocusOwner is " + keyboardFocusManager.permanentFocusOwner
                                                                          }
  )
}

context(lambdaIdeContext: LambdaIdeContext)
suspend fun JComponent.requestAndWaitFocus(componentName: String, attemptCount: Int = 3) {
  if (isFocusAncestor()) {
    frameworkLogger.info("Component ${(componentName)} is already focused")
    return
  }
  tryTimes(attemptCount, "Got focus for ${(componentName)}", 3.seconds) {
    requestFocus()
    waitForFocus(componentName, 10.seconds)
  }
}

context(lambdaIdeContext: LambdaIdeContext)
suspend fun ToolWindow.requestAndWaitFocus() {
  component.requestAndWaitFocus("toolwindow $id")
}