package com.intellij.jupyter.ui.test.util.project

import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.WelcomeScreenUI
import com.intellij.driver.sdk.ui.components.elements.JTreeUiComponent
import com.intellij.driver.sdk.ui.ui

fun WelcomeScreenUI.clickKotlinNotebooks(): Unit = xx("//div[@class='Tree']", JTreeUiComponent::class.java).list()
  .takeIf { it.isNotEmpty() }?.get(0)?.clickPath("Kotlin Notebooks") ?: error("Left side tree is not found in welcome screen")

fun WelcomeScreenUI.withNewNotebookDialog(action: NewNotebookDialog.() -> Unit) {
  clickKotlinNotebooks()
  x("//div[@class='JBOptionButton' and @text='New Notebook']").setFocus()
  keyboard { enter() } // it works better than click() here. For some reason click retries 3 times with the 'Click was unsuccessful' warning while the expected dialog already on the screen
  driver.ui.x("//div[@class='MyDialog']", NewNotebookDialog::class.java)
    .action()
}

class NewNotebookDialog(componentData: ComponentData) : UiComponent(componentData) {
  val scratchTypeButton: UiComponent
    get() = x("//div[@class='SegmentedButton' and @visible_text='Scratch']")
  val standardTypeButton: UiComponent
    get() = x("//div[@class='SegmentedButton' and @visible_text='In Folder']")

  val createButton: UiComponent
    get() = x("//div[@class='JButton' and @visible_text='Create']")
}
