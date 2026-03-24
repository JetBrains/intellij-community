package com.intellij.lambda.testFramework.testApi

import com.intellij.openapi.actionSystem.impl.ActionMenuItem
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.asContextElement
import com.intellij.remoteDev.tests.impl.utils.waitSuspendingNotNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Window
import javax.swing.JPopupMenu
import javax.swing.RootPaneContainer
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

suspend fun waitForPopupMenu(timeout: Duration = 15.seconds): JPopupMenu {
  return waitSuspendingNotNull("Popup Menu", timeout) {
    withContext(Dispatchers.UI + ModalityState.any().asContextElement()) {
      Window.getWindows()
        .asSequence()
        .filter { it.isVisible }
        .filterIsInstance<RootPaneContainer>()
        .firstNotNullOfOrNull { it.contentPane.components.firstOrNull() as? JPopupMenu }
    }
  }
}

suspend fun JPopupMenu.selectItem(itemText: String) {
  val item = waitSuspendingNotNull("Menu item", 5.seconds) {
    withContext(Dispatchers.UI + ModalityState.any().asContextElement()) {
      subElements.filterIsInstance<ActionMenuItem>().firstOrNull { it.text == itemText }
    }
  }
  mouseLeftClick(item)
}