// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.packages

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.fields.ExtendableTextComponent
import com.intellij.ui.components.fields.ExtendableTextField
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.toolwindow.PyPackagingToolWindowService
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.KeyEvent
import javax.swing.JList
import javax.swing.KeyStroke
import javax.swing.event.DocumentEvent

internal class PyPackageSearchTextField(private val project: Project) : ExtendableTextField(), Disposable.Default {
  val service
    get() = PyPackagingToolWindowService.getInstance(project)

  private val historyHelper = object : SearchTextField(HISTORY_KEY) {
    override fun isShowing() = true
    public override fun showPopup() = super.showPopup()
    override fun getPopupLocationComponent() = this@PyPackageSearchTextField
    override fun createItemChosenCallback(list: JList<*>) = Runnable {
      val value = (list.selectedValue ?: return@Runnable) as String
      this@PyPackageSearchTextField.text = value
      addCurrentTextToHistory()
    }
  }

  private var clearExtension: ExtendableTextComponent.Extension? = null

  init {
    emptyText.text = PyBundle.message("python.toolwindow.packages.search.text.placeholder")

    addExtension(object : ExtendableTextComponent.Extension {
      override fun getIcon(hovered: Boolean) = AllIcons.Actions.Search
      override fun isIconBeforeText() = true
      override fun getActionOnClick() = Runnable { historyHelper.showPopup() }
    })

    addFocusListener(object : FocusAdapter() {
      override fun focusLost(e: FocusEvent) {
        historyHelper.text = text
        historyHelper.addCurrentTextToHistory()
      }
    })

    document.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        updateClearButton()
        service.handleSearch(text.trim())
      }
    })

    registerKeyboardAction(
      { if (text.isNotEmpty()) { text = ""; service.handleSearch("") } },
      KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
        WHEN_FOCUSED,
    )
  }

  private fun updateClearButton() {
    if (text.isEmpty()) {
      clearExtension?.let { removeExtension(it); clearExtension = null }
    }
    else if (clearExtension == null) {
      val ext = ExtendableTextComponent.Extension.create(
        AllIcons.Actions.Close,
        AllIcons.Actions.CloseHovered,
        PyBundle.message("python.toolwindow.packages.search.clear.tooltip"),
        Runnable { text = ""; service.handleSearch("") }
      )
      clearExtension = ext
      addExtension(ext)
    }
  }

  companion object {
    private const val HISTORY_KEY = "PyPackageSearchTextField.history"
  }
}
