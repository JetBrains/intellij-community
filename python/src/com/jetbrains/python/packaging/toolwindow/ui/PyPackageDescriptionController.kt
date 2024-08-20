// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.ui

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.components.service
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.observable.properties.AtomicProperty
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.util.and
import com.intellij.openapi.observable.util.isNotNull
import com.intellij.openapi.observable.util.transform
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.JBColor
import com.intellij.ui.SideBorder
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.jcef.JCEFHtmlPanel
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.packaging.PyPackageUtil
import com.jetbrains.python.packaging.common.PythonPackageDetails
import com.jetbrains.python.packaging.toolwindow.PyPackagingJcefHtmlPanel
import com.jetbrains.python.packaging.toolwindow.PyPackagingToolWindowService
import com.jetbrains.python.packaging.toolwindow.actions.DeletePackageAction
import com.jetbrains.python.packaging.toolwindow.actions.InstallPackageAction
import com.jetbrains.python.packaging.toolwindow.model.DisplayablePackage
import com.jetbrains.python.packaging.toolwindow.model.InstallablePackage
import com.jetbrains.python.packaging.toolwindow.model.InstalledPackage
import com.jetbrains.python.packaging.toolwindow.ui.PyPackagesUiComponents.CUSTOM_PROGRESS_BAR_DATA_CONTEXT
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import kotlinx.coroutines.Dispatchers
import java.awt.BorderLayout
import java.util.*
import javax.swing.DefaultComboBoxModel
import javax.swing.JProgressBar

class PyPackageDescriptionController(val project: Project) : Disposable {
  val service = project.service<PyPackagingToolWindowService>()

  private val selectedPackage = AtomicProperty<DisplayablePackage?>(null)
  private val isManagement = AtomicBooleanProperty(false)

  private val selectedPackageDetails = AtomicProperty<PythonPackageDetails?>(null)

  private val packageVersionListModel = DefaultComboBoxModel<String>(Vector(0))


  private val packageNameProperty = selectedPackage.transform { it?.name ?: "" }
  private val packageVersionProperty: ObservableMutableProperty<@NlsSafe String> = AtomicProperty("")
  private val packageDocumentationProperty = selectedPackageDetails.transform { it?.documentationUrl }


  private val progressBar: JProgressBar = JProgressBar(JProgressBar.HORIZONTAL).apply {
    maximumSize.width = 200
    minimumSize.width = 200
    preferredSize.width = 200
    isVisible = false
    isIndeterminate = true
  }

  private val htmlPanel: JCEFHtmlPanel = PyPackagingJcefHtmlPanel(project).also { panel ->
    Disposer.register(this, panel)

    selectedPackageDetails.afterChange { packageDetails ->
      packageDetails ?: return@afterChange

      PyPackageCoroutine.launch(project, Dispatchers.Main) {
        service.getHtml(packageDetails)
      }
    }
  }

  private val leftPanel = panel {
    row {
      label("").bindText(packageNameProperty)
      link(message("python.toolwindow.packages.documentation.link")) {
        val docLink = packageDocumentationProperty.get() ?: return@link
        BrowserUtil.browse(docLink)
      }.visibleIf(packageDocumentationProperty.isNotNull())
    }.topGap(TopGap.NONE).bottomGap(BottomGap.NONE)
  }

  private val rightPanel = panel {
    row {
      cell(progressBar).gap(RightGap.SMALL)

      actionButton(InstallPackageAction())
        .visibleIf(selectedPackage.transform { it is InstallablePackage }.and(isManagement))
        .gap(RightGap.SMALL)

      val comboBox = comboBox(packageVersionListModel, CustomListCellRenderer<String> { it })
      comboBox.onChanged {
        val newVersion = (it.selectedItem as? String) ?: ""
        updatePackageVersion(newVersion)
      }
      comboBox.visibleIf(selectedPackage.transform { it is InstalledPackage }.and(isManagement))
      packageVersionProperty.afterChange {
        comboBox.component.selectedItem = it
      }

      actionButton(DeletePackageAction())
        .visibleIf(selectedPackage.transform { it is InstalledPackage }.and(isManagement))
        .gap(RightGap.SMALL)
    }.topGap(TopGap.NONE).bottomGap(BottomGap.NONE)
  }


  private val component = PyPackagesUiComponents.borderPanel {
    add(PyPackagesUiComponents.borderPanel {
      border = SideBorder(JBColor.GRAY, SideBorder.BOTTOM)
      //preferredSize = Dimension(preferredSize.width, 50)
      //minimumSize = Dimension(minimumSize.width, 50)
      //maximumSize = Dimension(maximumSize.width, 50)
      add(leftPanel, BorderLayout.WEST)
      add(rightPanel, BorderLayout.EAST)
    }, BorderLayout.NORTH)
    add(htmlPanel.component, BorderLayout.CENTER)
  }

  val wrappedComponent = UiDataProvider.wrapComponent(component, UiDataProvider {
    it[CUSTOM_PROGRESS_BAR_DATA_CONTEXT] = progressBar
  })

  override fun dispose() {}

  fun setPackage(pyPackage: DisplayablePackage) {
    selectedPackage.set(pyPackage)
    isManagement.set(PyPackageUtil.packageManagementEnabled(service.currentSdk, true, false))
  }

  fun setPackageDetails(packageDetails: PythonPackageDetails) {
    selectedPackageDetails.set(packageDetails)
    packageVersionListModel.removeAllElements()
    packageVersionListModel.addAll(packageDetails.availableVersions)
    packageVersionProperty.set((selectedPackage.get() as? InstalledPackage)?.currentVersion?.presentableText ?: "")
  }


  private fun updatePackageVersion(newVersion: String) {
    val details = selectedPackageDetails.get() ?: return
    val newVersionSpec = details.toPackageSpecification(newVersion)
    val pyPackagingToolWindowService = PyPackagingToolWindowService.getInstance(project)
    PyPackageCoroutine.launch(project) {
      pyPackagingToolWindowService.installPackage(newVersionSpec)
    }
  }
}