// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.details

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.observable.properties.AtomicProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Disposer
import com.jetbrains.python.packaging.common.PythonPackageManagementListener
import com.jetbrains.python.packaging.management.PythonPackageManager
import kotlinx.coroutines.launch
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBPanelWithEmptyText
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.packaging.toolwindow.PyPackagingToolWindowService
import com.jetbrains.python.packaging.toolwindow.model.DependencyGroupNode
import com.jetbrains.python.packaging.toolwindow.model.DisplayablePackage
import com.jetbrains.python.packaging.toolwindow.model.InstallablePackage
import com.jetbrains.python.packaging.toolwindow.model.InstalledPackage
import com.jetbrains.python.packaging.toolwindow.model.LoadingNode
import com.jetbrains.python.packaging.toolwindow.model.RequirementPackage
import com.jetbrains.python.packaging.toolwindow.model.UndeclaredPackagesGroup
import com.jetbrains.python.packaging.toolwindow.model.WorkspaceMember
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

internal class PyPackageInfoPanel(
  val project: Project,
  onSelectInTree: ((String) -> Unit)? = null,
) : Disposable {
  private val infoController = PyPackageDescriptionController(
    project = project,
    onSelectInTree = onSelectInTree,
    onActionCompleted = { clear() },
  ).also {
    Disposer.register(this, it)
  }

  init {
    // After an install or uninstall the tree row for the affected package is rebuilt — the doc
    // panel would otherwise keep rendering the dangling `DisplayablePackage` reference, complete
    // with stale "installed"/"available" affordances. Reset the pane to the placeholder so the
    // user re-selects from the freshly-rebuilt list (PY-89838 follow-up).
    project.messageBus.connect(this).subscribe(
      PythonPackageManager.PACKAGE_MANAGEMENT_TOPIC,
      object : PythonPackageManagementListener {
        override fun packagesChanged(sdk: Sdk) {
          val service = project.service<PyPackagingToolWindowService>()
          service.serviceScope.launch(Dispatchers.EDT) { clear() }
        }
      }
    )
  }

  private val packageProperty = AtomicProperty<DisplayablePackage?>(null)

  // [StatusText.setShowAboveCenter] defaults to true, which paints the empty-text at the upper
  // third of the panel. The doc preview pane is tall when the tool window is anchored at the
  // bottom, and an upper-third placeholder looks misaligned next to the centered list rows on
  // the left, so we pin both empty-state panels to true vertical centre.
  private val noPackagePanel = JBPanelWithEmptyText().apply {
    emptyText.text = message("python.toolwindow.packages.description.panel.placeholder")
    emptyText.setShowAboveCenter(false)
  }
  private val loadingPanel = JBPanelWithEmptyText().apply {
    emptyText.appendLine(AnimatedIcon.Default.INSTANCE, message("python.toolwindow.packages.description.panel.loading"), SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES, null)
    emptyText.setShowAboveCenter(false)
  }

  private var updateJob: Job? = null

  val component: JPanel = JPanel(BorderLayout()).apply {
    add(noPackagePanel, BorderLayout.CENTER)
  }

  override fun dispose() {}

  fun getPackage(): DisplayablePackage? = packageProperty.get()

  fun setPackage(pyPackage: DisplayablePackage?) {
    val isDocumentable = when (pyPackage) {
      is InstalledPackage, is InstallablePackage, is RequirementPackage -> true
      is WorkspaceMember, is DependencyGroupNode, is UndeclaredPackagesGroup, is LoadingNode, null -> false
    }
    if (!isDocumentable) {
      updateJob?.cancel()
      packageProperty.set(null)
      setComponent(noPackagePanel)
      return
    }
    pyPackage!!

    setComponent(loadingPanel)
    packageProperty.set(pyPackage)

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

  /**
   * Clear the doc preview so it no longer references a stale package. Called after
   * install/uninstall actions complete: the tree's [DisplayablePackage] for the affected name
   * is rebuilt and the previously-selected reference is dangling, so the info pane should fall
   * back to the placeholder until the user selects the freshly-rebuilt row.
   */
  fun clear() {
    setPackage(null)
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