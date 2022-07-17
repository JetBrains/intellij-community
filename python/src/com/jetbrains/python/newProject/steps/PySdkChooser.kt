// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.newProject.steps

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.ComboboxSpeedSearch
import com.intellij.ui.components.DropDownLink
import com.intellij.util.ui.JBUI
import com.jetbrains.python.PyBundle
import com.jetbrains.python.configuration.PyActiveSdkConfigurable
import com.jetbrains.python.configuration.PyConfigurableInterpreterList
import com.jetbrains.python.sdk.PySdkListCellRenderer
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.util.function.Consumer
import javax.swing.JComboBox
import javax.swing.JPanel

private val LOG = Logger.getInstance("#com.jetbrains.python.newProject.steps")

internal fun createPythonSdkComboBox(sdks: List<Sdk>, initialSelection: Sdk?): ComboBox<Sdk> {
  val comboBox = ComboBox<Sdk>()
  comboBox.model = CollectionComboBoxModel<Sdk>(sdks, initialSelection)
  comboBox.renderer = PySdkListCellRenderer()
  comboBox.addActionListener { comboBox.updateTooltip() }
  ComboboxSpeedSearch(comboBox)
  comboBox.updateTooltip()
  return comboBox
}

private fun ComboBox<*>.updateTooltip() {
  val item: Any? = getSelectedItem()
  val sdkHomePath = if (item is Sdk) item.homePath else null
  setToolTipText(if (sdkHomePath != null) FileUtil.toSystemDependentName(sdkHomePath) else null)
}

internal fun ComboBox<Sdk>.withAddInterpreterLink(project: Project?, module: Module?): JPanel {
  return withActionLink(PyBundle.message("active.sdk.dialog.link.add.interpreter.text")) { dropDownLink ->
    val interpreterList = PyConfigurableInterpreterList.getInstance(project)
    val oldSelectedSdk = selectedItem as Sdk?
    PyActiveSdkConfigurable.createAddInterpreterPopup(
      project ?: ProjectManager.getInstance().defaultProject,
      module,
      dropDownLink,
      Consumer { sdk: Sdk? ->
        if (sdk == null) return@Consumer
        val projectSdksModel = interpreterList.model
        if (projectSdksModel.findSdk(sdk) == null) {
          projectSdksModel.addSdk(sdk)
          try {
            projectSdksModel.apply()
          }
          catch (e: ConfigurationException) {
            LOG.error("Error adding new python interpreter " + e.message)
          }
        }
        val committedSdks = interpreterList.allPythonSdks
        val copiedSdk = interpreterList.model.findSdk(sdk.name)
        setModel(CollectionComboBoxModel(committedSdks, oldSelectedSdk))
        selectedItem = copiedSdk
      }
    )
  }
}

private fun <E> JComboBox<E>.withActionLink(dropDownLinkItem: String, popupBuilder: (DropDownLink<String>) -> JBPopup): JPanel {
  val result = JPanel(GridBagLayout())

  val c = GridBagConstraints()
  c.fill = GridBagConstraints.HORIZONTAL
  c.insets = JBUI.insets(2)

  c.gridx = 0
  c.gridy = 0
  c.weightx = 0.1
  result.add(this, c)

  c.insets = JBUI.insets(2, 14, 2, 2)
  c.gridx = 1
  c.gridy = 0
  c.weightx = 0.0
  result.add(DropDownLink(dropDownLinkItem, popupBuilder), c)

  return result
}