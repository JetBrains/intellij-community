// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.details

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.observable.properties.AtomicProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBPanelWithEmptyText
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.packaging.toolwindow.PyPackagingToolWindowService
import com.jetbrains.python.packaging.toolwindow.model.DisplayablePackage
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

class PyPackageInfoPanel(val project: Project) : Disposable {
  private val infoController = PyPackageDescriptionController(project).also {
    Disposer.register(this, it)
  }

  private val packageProperty = AtomicProperty<DisplayablePackage?>(null)

  private val noPackagePanel = JBPanelWithEmptyText().apply { emptyText.text = message("python.toolwindow.packages.description.panel.placeholder") }
  private val loadingPanel = JBPanelWithEmptyText().apply {
    emptyText.appendLine(AnimatedIcon.Default.INSTANCE, message("python.toolwindow.packages.description.panel.loading"), SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES, null)
  }

  private var updateJob: Job? = null

  val component = JPanel().apply {
    layout = BorderLayout()
  }

  override fun dispose() {}

  fun getPackage() = packageProperty.get()

  fun setPackage(pyPackage: DisplayablePackage?) {
    val newPanel = if (pyPackage == null) noPackagePanel else loadingPanel
    setComponent(newPanel)

    packageProperty.set(pyPackage)

    if (pyPackage == null) {
      return
    }

    infoController.setPackage(pyPackage)
    updateJob?.cancel()

    val service = project.service<PyPackagingToolWindowService>()
    updateJob = PyPackageCoroutine.launch(project) {
      try {
        val packageDetails = service.detailsForPackage(pyPackage) ?: return@launch

        withContext(Dispatchers.EDT) {
          infoController.setPackageDetails(packageDetails)
        }
      }
      finally {
        withContext(Dispatchers.EDT) {
          setComponent(infoController.wrappedComponent)
        }
      }
    }
  }

  private fun setComponent(newPanel: JComponent) {
    val currentComponent = component.components.firstOrNull()
    if (currentComponent != newPanel) {
      component.removeAll()
      component.add(newPanel)
      component.revalidate()
      component.repaint()
    }
  }
}