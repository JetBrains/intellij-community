// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.details

import com.intellij.ide.BrowserUtil
import com.intellij.ide.plugins.newui.OneLineProgressIndicator
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.observable.properties.AtomicProperty
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.util.and
import com.intellij.openapi.observable.util.isNotNull
import com.intellij.openapi.observable.util.not
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
import com.intellij.ui.components.JBComboBoxLabel
import com.intellij.ui.components.JBOptionButton
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.jcef.JCEFHtmlPanel
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.packaging.PyPackageUtil
import com.jetbrains.python.packaging.common.PythonPackageDetails
import com.jetbrains.python.packaging.management.toInstallRequest
import com.jetbrains.python.packaging.toolwindow.PyPackagingToolWindowService
import com.jetbrains.python.packaging.toolwindow.actions.InstallWithOptionsPackageAction
import com.jetbrains.python.packaging.toolwindow.model.*
import com.jetbrains.python.packaging.toolwindow.ui.PyPackagesUiComponents
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import kotlinx.coroutines.Dispatchers
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

  val service: PyPackagingToolWindowService = project.service<PyPackagingToolWindowService>()

  internal val selectedPackage = AtomicProperty<DisplayablePackage?>(null)
  private val isManagement = AtomicBooleanProperty(false)

  private val selectedPackageDetails = AtomicProperty<PythonPackageDetails?>(null)

  private val packageNameProperty = selectedPackage.transform { it?.name ?: "" }
  private val packageVersionProperty: ObservableMutableProperty<@NlsSafe String> = AtomicProperty(latestText)

  private val packageDocumentationProperty = selectedPackageDetails.transform { it?.documentationUrl }

  private val installActionButton = JBOptionButton(null, emptyArray())

  private val installAction = wrapAction(message("action.PyInstallPackage.text"), message("progress.text.installing")) {
    val details = selectedPackageDetails.get() ?: return@wrapAction
    val version = versionSelector.text.takeIf { it != latestText }
    val specification = details.toPackageSpecification(version) ?: return@wrapAction
    project.service<PyPackagingToolWindowService>().installPackage(specification.toInstallRequest())
  }

  private val installWithOptionAction: Action = wrapAction(message("action.PyInstallWithOptionPackage.text"), message("progress.text.installing")) {
    val details = selectedPackageDetails.get() ?: return@wrapAction
    val version = versionSelector.text.takeIf { it != latestText }
    InstallWithOptionsPackageAction.installWithOptions(project, details, version)
  }

  private val versionSelector = JBComboBoxLabel()

  private val progressEnabledProperty = AtomicBooleanProperty(false)

  private val progressIndicatorComponent = JPanel()

  private val htmlPanel: JCEFHtmlPanel = PyPackagingJcefHtmlPanel(project).also { panel ->
    Disposer.register(this, panel)

    selectedPackageDetails.afterChange { packageDetails ->
      packageDetails ?: return@afterChange

      PyPackageCoroutine.launch(project, Dispatchers.Default) {
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
      cell(progressIndicatorComponent).gap(RightGap.SMALL).visibleIf(progressEnabledProperty)
      versionSelector.apply {
        versionSelector.text = packageVersionProperty.get()
        addMouseListener(object : MouseAdapter() {
          override fun mouseClicked(e: MouseEvent?) {
            val availableVersions = selectedPackageDetails.get()?.availableVersions ?: emptyList()
            val latestVersion = availableVersions.first()
            val versions = listOf(latestText) + availableVersions
            JBPopupFactory.getInstance().createListPopup(
              object : BaseListPopupStep<String>(null, versions) {
                override fun onChosen(@NlsContexts.Label selectedValue: String, finalChoice: Boolean): PopupStep<*>? {
                  packageVersionProperty.set(selectedValue)
                  val effectiveVersion = if (selectedValue == latestText) latestVersion else selectedValue
                  suggestInstallPackage(effectiveVersion)
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
      comboBox.enabledIf(isManagement.and(progressEnabledProperty.not())).gap(RightGap.SMALL)
      comboBox.visibleIf(progressEnabledProperty.not().and(
        selectedPackage.transform { pkg ->
          when (pkg) {
            is InstallablePackage, is InstalledPackage -> true
            is RequirementPackage, is ExpandResultNode, is DisplayablePackage, null -> false
          }
        }
      ))

      installActionButton.action = installAction
      installActionButton.options = arrayOf(installWithOptionAction)
      cell(installActionButton).visibleIf(selectedPackage.transform { it is InstallablePackage }.and(isManagement).and(progressEnabledProperty.not()))
        .gap(RightGap.SMALL)

      button(message("action.PyDeletePackage.text")) {
        wrapInvokeOp(progressText = message("python.toolwindow.packages.deleting.text")) {
          val pyPackage = selectedPackage.get() as? InstalledPackage ?: return@wrapInvokeOp
          project.service<PyPackagingToolWindowService>().deletePackage(pyPackage)
        }
      }.visibleIf(selectedPackage.transform { it is InstalledPackage }.and(isManagement).and(progressEnabledProperty.not()))
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

  val wrappedComponent: JComponent = UiDataProvider.wrapComponent(component, UiDataProvider {})

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
    val newVersionSpec = details.toPackageSpecification(newVersion) ?: return
    val pyPackagingToolWindowService = PyPackagingToolWindowService.getInstance(project)
    PyPackageCoroutine.launch(project, Dispatchers.IO) {
      pyPackagingToolWindowService.installPackage(newVersionSpec.toInstallRequest())
    }
  }

  private fun suggestInstallPackage(selectedValue: String) {
    if (selectedPackage.get() !is InstalledPackage)
      return

    wrapInvokeOp(message("progress.text.installing")) {
      updatePackageVersion(selectedValue)
    }
  }

  private fun calculateVersionText() = (selectedPackage.get() as? InstalledPackage)?.currentVersion?.presentableText ?: latestText

  private fun wrapAction(@Nls text: String, @Nls progressText: String, actionPerformed: suspend () -> Unit): Action = object : AbstractAction(text) {
    override fun actionPerformed(e: ActionEvent) {
      wrapInvokeOp(progressText, actionPerformed)
    }
  }

  private fun wrapInvokeOp(@Nls progressText: String, actionPerformed: suspend () -> Unit) {
    progressEnabledProperty.set(true)
    val progressIndicator = OneLineProgressIndicator(true, true)
    progressIndicator.text = progressText
    progressIndicatorComponent.removeAll()
    progressIndicatorComponent.add(progressIndicator.component, BorderLayout.CENTER)

    val job = PyPackageCoroutine.launch(project, Dispatchers.IO) {
      try {
        progressIndicator.start()
        actionPerformed()
      }
      finally {
        withContext(Dispatchers.EDT) {
          progressEnabledProperty.set(false)
        }
        progressIndicator.stop()
      }
    }

    progressIndicator.setCancelRunnable {
      job.cancel()
    }
  }
}