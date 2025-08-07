// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.packages

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.intellij.util.cancelOnDispose
import com.intellij.util.ui.JBUI
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.toolwindow.PyPackagingToolWindowService
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import java.awt.Dimension
import javax.swing.event.DocumentEvent
import kotlin.time.Duration.Companion.seconds

@OptIn(FlowPreview::class)
internal class PyPackageSearchTextField(private val project: Project) : SearchTextField(true), Disposable.Default {
  val service
    get() = PyPackagingToolWindowService.getInstance(project)

  val historyUpdateFlow = MutableStateFlow(Unit)

  init {
    service.serviceScope.launch {
      historyUpdateFlow.debounce(3.seconds).collect {
        addCurrentTextToHistory()
      }
    }.cancelOnDispose(this)
  }


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
        historyUpdateFlow.tryEmit(Unit)
        service.handleSearch(text.trim())
      }
    })

  }

  override fun onFieldCleared() {
    service.handleSearch("")
  }
}