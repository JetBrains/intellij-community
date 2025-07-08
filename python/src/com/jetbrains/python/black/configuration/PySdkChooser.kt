// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.black.configuration

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.ComboboxSpeedSearch
import com.jetbrains.python.sdk.PySdkListCellRenderer

internal fun createPythonSdkComboBox(sdks: List<Sdk>, initialSelection: Sdk?): ComboBox<Sdk> {
  val comboBox = ComboBox<Sdk>()
  comboBox.model = CollectionComboBoxModel(sdks.toMutableList(), initialSelection)
  comboBox.renderer = PySdkListCellRenderer()
  comboBox.addActionListener { comboBox.updateTooltip() }
  ComboboxSpeedSearch.installOn(comboBox)
  comboBox.updateTooltip()
  return comboBox
}

private fun ComboBox<*>.updateTooltip() {
  val item: Any? = getSelectedItem()
  val sdkHomePath = if (item is Sdk) item.homePath else null
  setToolTipText(if (sdkHomePath != null) FileUtil.toSystemDependentName(sdkHomePath) else null)
}

