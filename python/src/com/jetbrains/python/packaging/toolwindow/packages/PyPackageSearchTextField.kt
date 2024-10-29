// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.packages

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.intellij.util.Alarm
import com.intellij.util.SingleAlarm
import com.intellij.util.ui.JBUI
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.toolwindow.PyPackagingToolWindowService
import java.awt.Dimension
import javax.swing.event.DocumentEvent

internal class PyPackageSearchTextField(private val project: Project) : SearchTextField(true) {
  val service
    get() = PyPackagingToolWindowService.getInstance(project)

  val searchAlarm = SingleAlarm(
    task = {
      addCurrentTextToHistory()
      service.handleSearch(text.trim())
    },
    delay = 500,
    parentDisposable = null,
    threadToUse = Alarm.ThreadToUse.SWING_THREAD,
    coroutineScope = service.serviceScope,
    modalityState = ModalityState.nonModal(),
  )

  init {
    setHistoryPropertyName("PyPackageSearchTextField.history")
    setHistorySize(10)

    preferredSize = Dimension(250, 30)
    minimumSize = Dimension(250, 30)
    maximumSize = Dimension(250, 30)
    textEditor.border = JBUI.Borders.emptyLeft(6)
    textEditor.isOpaque = true
    textEditor.emptyText.text = PyBundle.message("python.toolwindow.packages.search.text.placeholder")


    addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        searchAlarm.cancelAndRequest()
      }
    })

  }

  override fun onFieldCleared() {
    service.handleSearch("")
  }
}