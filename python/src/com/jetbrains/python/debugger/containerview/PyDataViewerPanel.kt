// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger.containerview

import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CompositeDataProvider
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.project.Project
import com.jetbrains.python.debugger.PyDebugValue
import com.jetbrains.python.debugger.PyFrameAccessor
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

class PyDataViewerPanel(
  val project: Project,
  val frameAccessor: PyFrameAccessor,
  private var isPanelFromFactory: Boolean = false,
) : JPanel(BorderLayout()), Disposable {

  var component: PyDataViewerAbstractPanel

  init {
    val dataViewerModel = PyDataViewerModel(project, frameAccessor)

    var dataViewerPanel = if (isPanelsFromFactoryAvailable()) {
      isPanelFromFactory = true
      createPanelFromFactory(dataViewerModel)
    }
    else {
      PyDataViewerCommunityPanel(dataViewerModel)
    }

    component = dataViewerPanel
    add(component)

    setupDataProvider()

    if (isPanelsFromFactoryAvailable() && !isPanelFromFactory) {
      PyDataViewerPanelHelper.createGotItTooltip(tablePanel = component, tableParentPanel = this)
    }
  }

  fun getDataViewerModel(): PyDataViewerModel {
    return component.dataViewerModel
  }

  fun addListener(onNameChangedListener: PyDataViewerAbstractPanel.OnNameChangedListener) {
    component.addListener(onNameChangedListener)
  }

  fun switchBetweenCommunityAndFactoriesTables() {
    isPanelFromFactory = !isPanelFromFactory

    val dataViewerModel = getDataViewerModel()
    val debugValue = dataViewerModel.debugValue
    if (debugValue != null && !isSupportedByCommunityDataViewer(debugValue)) {
      isPanelFromFactory = true
      PyDataViewerPanelHelper.showCommunityDataViewerRestrictionsBalloon(tablePanel = component, tableParentPanel = this)
      return
    }

    component = if (isPanelFromFactory && isPanelsFromFactoryAvailable()) {
      createPanelFromFactory(dataViewerModel)
    }
    else {
      PyDataViewerCommunityPanel(dataViewerModel)
    }
    component.recreateTable()

    removeAll()
    add(component)

    if (debugValue != null) {
      component.apply(debugValue, false)
    }
    else {
      component.apply(dataViewerModel.slicing, false)
    }
  }

  private fun isPanelsFromFactoryAvailable(): Boolean {
    return PyDataViewPanelFactory.EP_NAME.extensionList.isNotEmpty()
  }

  private fun createPanelFromFactory(dataViewerModel: PyDataViewerModel): PyDataViewerAbstractPanel {
    return PyDataViewPanelFactory.EP_NAME.extensionList.first().createDataViewerPanel(dataViewerModel)
  }

  /**
   * Old tables cannot work properly with polars dataframes.
   */
  private fun isSupportedByCommunityDataViewer(debugValue: PyDebugValue): Boolean {
    return debugValue.typeQualifier != "polars.dataframe.frame"
  }

  private fun setupDataProvider() {
    val toolbarDataProvider = DataProvider { dataId ->
      if (PY_DATA_VIEWER_PANEL_KEY.`is`(dataId)) this@PyDataViewerPanel else null
    }

    addDataProvider(this, toolbarDataProvider)
  }

  override fun dispose() {}

  companion object {

    val PY_DATA_VIEWER_PANEL_KEY: DataKey<PyDataViewerPanel> = DataKey.create<PyDataViewerPanel>("PY_DATA_VIEWER_PANEL_KEY")

    fun addDataProvider(component: JComponent, provider: DataProvider) {
      val currentProvider = DataManager.getDataProvider(component)
      if (currentProvider != null) {
        DataManager.removeDataProvider(component)
        DataManager.registerDataProvider(component, CompositeDataProvider.compose(currentProvider, provider))
      }
      else {
        DataManager.registerDataProvider(component, provider)
      }
    }
  }
}