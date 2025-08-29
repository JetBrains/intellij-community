// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.externaltools.configuration

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.ComboboxSpeedSearch
import com.jetbrains.python.sdk.PySdkListCellRenderer
import com.jetbrains.python.sdk.PythonSdkUtil
import com.jetbrains.python.sdk.pythonSdk

const val DEFAULT_ENVIRONMENT: String = "Project Default"

fun Project.sdkByNameDefaultAware(name: String): Sdk? =
  if (name == DEFAULT_ENVIRONMENT) pythonSdk else PythonSdkUtil.findSdkByKey(name)

fun createPythonSdkComboBox(project: Project, initialSelection: Any?): ComboBox<Any> {
  val sdks = buildList {
    project.pythonSdk?.let { add(it) }
    project.modules.mapNotNullTo(this) { it.pythonSdk }
  }.distinct()
  return ComboBox<Any>().apply {
    model = CollectionComboBoxModel(
      // we use a simple string here, ideally we should introduce some special class for this
      (listOf(DEFAULT_ENVIRONMENT) + sdks).toMutableList(),
      initialSelection,
    )
    renderer = PySdkListCellRenderer()
    addActionListener { updateTooltip() }
    ComboboxSpeedSearch.installOn(this)
    updateTooltip()
  }
}

private fun ComboBox<*>.updateTooltip() {
  val item = selectedItem
  val sdkHomePath = if (item is Sdk) item.homePath else null
  setToolTipText(if (sdkHomePath != null) FileUtil.toSystemDependentName(sdkHomePath) else null)
}
