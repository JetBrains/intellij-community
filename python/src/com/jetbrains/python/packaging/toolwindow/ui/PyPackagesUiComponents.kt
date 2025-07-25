// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.ui.SideBorder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.NamedColorUtil
import com.intellij.util.ui.UIUtil
import com.jetbrains.python.packaging.common.NormalizedPythonPackageName
import com.jetbrains.python.packaging.common.PythonPackageDetails
import com.jetbrains.python.packaging.management.toInstallRequest
import com.jetbrains.python.packaging.pyRequirement
import com.jetbrains.python.packaging.pyRequirementVersionSpec
import com.jetbrains.python.packaging.toolwindow.PyPackagingToolWindowService
import com.jetbrains.python.packaging.toolwindow.model.DisplayablePackage
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import kotlinx.coroutines.Dispatchers
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*

object PyPackagesUiComponents {
  val SELECTED_PACKAGE_DATA_CONTEXT: DataKey<DisplayablePackage> = DataKey.create<DisplayablePackage>("SELECTED_PACKAGE_DATA_CONTEXT")
  val SELECTED_PACKAGES_DATA_CONTEXT: DataKey<List<DisplayablePackage>> = DataKey.create<List<DisplayablePackage>>("SELECTED_PACKAGES_DATA_CONTEXT")

  internal val AnActionEvent.selectedPackage: DisplayablePackage?
    get() = getData(SELECTED_PACKAGE_DATA_CONTEXT)

  internal val AnActionEvent.selectedPackages: List<DisplayablePackage>
    get() = getData(SELECTED_PACKAGES_DATA_CONTEXT) ?: emptyList()

  fun createAvailableVersionsPopup(selectedPackage: DisplayablePackage, details: PythonPackageDetails, project: Project): ListPopup {
    return JBPopupFactory.getInstance().createListPopup(object : BaseListPopupStep<String>(null, details.availableVersions) {
      override fun onChosen(selectedValue: String?, finalChoice: Boolean): PopupStep<*>? {
        return doFinalStep {
          val repository = checkNotNull(selectedPackage.repository)
          val packageName = NormalizedPythonPackageName.from(selectedPackage.name).name
          val version = selectedValue?.let { pyRequirementVersionSpec(it) }
          val specification = repository.findPackageSpecification(pyRequirement(packageName, version))
          PyPackageCoroutine.launch(project, Dispatchers.IO) {
            project.service<PyPackagingToolWindowService>().installPackage(specification!!.toInstallRequest())
          }
        }
      }
    }, 8)
  }

  fun boxPanel(init: JPanel.() -> Unit): JPanel = object : JPanel() {
    init {
      layout = BoxLayout(this, BoxLayout.X_AXIS)
      alignmentX = LEFT_ALIGNMENT
      init()
    }
  }

  fun borderPanel(init: JPanel.() -> Unit): JPanel = object : JPanel() {
    init {
      layout = BorderLayout(0, 0)
      init()
    }
  }

  fun headerPanel(label: JLabel, component: JComponent?): JPanel = object : JPanel() {
    init {
      background = UIUtil.getLabelBackground()
      layout = BorderLayout()
      border = BorderFactory.createCompoundBorder(SideBorder(NamedColorUtil.getBoundsColor(), SideBorder.BOTTOM), JBUI.Borders.empty(0, 5))
      preferredSize = Dimension(preferredSize.width, 25)
      minimumSize = Dimension(minimumSize.width, 25)
      maximumSize = Dimension(maximumSize.width, 25)

      add(label, BorderLayout.WEST)
      if (component != null) {
        addMouseListener(object : MouseAdapter() {
          override fun mouseClicked(e: MouseEvent?) {
            component.isVisible = !component.isVisible
            label.icon = if (component.isVisible) AllIcons.General.ArrowDown else AllIcons.General.ArrowRight
            val parent = component.parent
            parent.revalidate()
            parent.repaint()
          }
        })
      }
    }
  }
}