// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.details

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.observable.properties.AtomicProperty
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.util.and
import com.intellij.openapi.observable.util.isNotNull
import com.intellij.openapi.observable.util.transform
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.JBColor
import com.intellij.ui.SideBorder
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBComboBoxLabel
import com.intellij.ui.components.JBOptionButton
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.jcef.JCEFHtmlPanel
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.packaging.PyPackageUtil
import com.jetbrains.python.packaging.common.PythonPackageDetails
import com.jetbrains.python.packaging.toolwindow.PyPackagingToolWindowService
import com.jetbrains.python.packaging.toolwindow.model.DisplayablePackage
import com.jetbrains.python.packaging.toolwindow.model.InstallablePackage
import com.jetbrains.python.packaging.toolwindow.model.InstalledPackage
import com.jetbrains.python.packaging.toolwindow.ui.PyPackagesUiComponents
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.Font
import java.awt.event.ActionEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*

class PyPackageDescriptionController(val project: Project) : Disposable {
  private val latestText: String
    get() = message("python.toolwindow.packages.latest.version.label")

  val service = project.service<PyPackagingToolWindowService>()

  private val selectedPackage = AtomicProperty<DisplayablePackage?>(null)
  private val isManagement = AtomicBooleanProperty(false)

  private val selectedPackageDetails = AtomicProperty<PythonPackageDetails?>(null)

  private val packageNameProperty = selectedPackage.transform { it?.name ?: "" }
  private val packageVersionProperty: ObservableMutableProperty<@NlsSafe String> = AtomicProperty(latestText)

  private val packageDocumentationProperty = selectedPackageDetails.transform { it?.documentationUrl }

  private val installActionButton = JBOptionButton(null, emptyArray())

  private val installAction = wrapAction(message("action.PyInstallPackage.text")) {
    installSelectedPackage()
  }


  private val installWithOptionAction: Action = wrapAction(message("action.PyChangeInstallWithOption.text")) {
    val pkg = selectedPackage.get() ?: return@wrapAction
    val details = selectedPackageDetails.get() ?: return@wrapAction
    withContext(Dispatchers.EDT) {
      val mousePosition = RelativePoint(component.mousePosition)
      PyPackagesUiComponents.createAvailableVersionsPopup(pkg, details, project).show(
        mousePosition)
    }
  }


  private val versionSelector = JBComboBoxLabel()

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
        val render = PyPackageDetailsHtmlRender(project, service.currentSdk)
        val html = render.getHtml(packageDetails)
        panel.setHtml(html)
      }
    }
  }

  private val leftPanel = panel {
    row {
      val packageNameLabel = label("").bindText(packageNameProperty).component
      packageNameLabel.verticalAlignment = SwingConstants.CENTER
      packageNameLabel.font = packageNameLabel.font.deriveFont(Font.BOLD)

      val docLink = link(message("python.toolwindow.packages.documentation.link")) {
        val docLink = packageDocumentationProperty.get() ?: return@link
        BrowserUtil.browse(docLink)
      }.visibleIf(packageDocumentationProperty.isNotNull())
      docLink.component.verticalAlignment = SwingConstants.CENTER
    }.topGap(TopGap.SMALL).bottomGap(BottomGap.NONE).resizableRow()
  }

  private val rightPanel = panel {
    row {
      cell(progressBar).gap(RightGap.SMALL)

      versionSelector.apply {

        versionSelector.text = packageVersionProperty.get()
        addMouseListener(object : MouseAdapter() {
          override fun mouseClicked(e: MouseEvent?) {
            val versions = listOf(latestText) + (selectedPackageDetails.get()?.availableVersions ?: emptyList())
            JBPopupFactory.getInstance().createListPopup(
              object : BaseListPopupStep<String>(null, versions) {
                override fun onChosen(@NlsContexts.Label selectedValue: String, finalChoice: Boolean): PopupStep<*>? {
                  packageVersionProperty.set(selectedValue)
                  suggestInstallPackage(selectedValue)
                  return FINAL_CHOICE
                }
              }, 8).showUnderneathOf(this@apply)
          }
        })
      }
      packageVersionProperty.afterChange {
        versionSelector.text = it
      }
      val comboBox = cell(versionSelector)
      comboBox.enabledIf(isManagement).gap(RightGap.SMALL)

      installActionButton.action = installAction
      installActionButton.options = arrayOf(installWithOptionAction)
      cell(installActionButton).visibleIf(selectedPackage.transform { it is InstallablePackage }.and(isManagement))
        .gap(RightGap.SMALL)

      button(message("action.PyDeletePackage.text")) {
        wrapInvokeOp {
          val pyPackage = selectedPackage.get() as? InstalledPackage ?: return@wrapInvokeOp
          project.service<PyPackagingToolWindowService>().deletePackage(pyPackage)
        }
      }.visibleIf(selectedPackage.transform { it is InstalledPackage }.and(isManagement))
        .gap(RightGap.SMALL)
    }.topGap(TopGap.NONE).bottomGap(BottomGap.NONE)
  }


  private val component = PyPackagesUiComponents.borderPanel {
    add(PyPackagesUiComponents.borderPanel {
      border = SideBorder(JBColor.GRAY, SideBorder.BOTTOM)
      leftPanel.border = BorderFactory.createEmptyBorder(0, 10, 0, 0)
      rightPanel.border = BorderFactory.createEmptyBorder(0, 0, 0, 10)

      add(leftPanel, BorderLayout.WEST)
      add(rightPanel, BorderLayout.EAST)
    }, BorderLayout.NORTH)
    add(htmlPanel.component, BorderLayout.CENTER)
  }

  val wrappedComponent = UiDataProvider.wrapComponent(component, UiDataProvider {})

  override fun dispose() {}

  fun setPackage(pyPackage: DisplayablePackage) {
    selectedPackage.set(pyPackage)
    packageVersionProperty.set(calculateVersionText())
    isManagement.set(PyPackageUtil.packageManagementEnabled(service.currentSdk, true, false))
  }

  fun setPackageDetails(packageDetails: PythonPackageDetails) {
    selectedPackageDetails.set(packageDetails)
  }


  private fun updatePackageVersion(newVersion: String) {
    val details = selectedPackageDetails.get() ?: return
    val newVersionSpec = details.toPackageSpecification(newVersion)
    val pyPackagingToolWindowService = PyPackagingToolWindowService.getInstance(project)
    PyPackageCoroutine.launch(project, Dispatchers.IO) {
      pyPackagingToolWindowService.installPackage(newVersionSpec)
    }
  }

  private suspend fun installSelectedPackage() {
    val details = selectedPackageDetails.get() ?: return
    val specification = details.repository.createPackageSpecification(details.name, details.availableVersions.first())
    project.service<PyPackagingToolWindowService>().installPackage(specification)
  }


  private fun suggestInstallPackage(selectedValue: String) {
    if (selectedPackage.get() !is InstalledPackage)
      return
    updatePackageVersion(selectedValue)
  }

  private fun calculateVersionText() = (selectedPackage.get() as? InstalledPackage)?.currentVersion?.presentableText ?: latestText


  private fun wrapAction(@Nls text: String, actionPerformed: suspend () -> Unit): Action = object : AbstractAction(text) {
    override fun actionPerformed(e: ActionEvent) {
      wrapInvokeOp(actionPerformed)
    }
  }

  private fun wrapInvokeOp(actionPerformed: suspend () -> Unit) {
    PyPackageCoroutine.getIoScope(project).launch(Dispatchers.IO) {
      actionPerformed()
    }
  }

}