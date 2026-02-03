// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger.containerview

import com.intellij.ide.DataManager
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CompositeDataProvider
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.jetbrains.python.debugger.PyDebugValue
import com.jetbrains.python.debugger.PyFrameAccessor
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

class PyDataViewerPanel(
  val project: Project,
  val frameAccessor: PyFrameAccessor,
  private var isPanelFromFactory: Boolean = false,
) : JPanel(BorderLayout()), Disposable.Default {

  var component: PyDataViewerAbstractPanel

  init {
    val dataViewerModel = PyDataViewerModel(project, frameAccessor)

    var dataViewerPanel = if (isPanelFromFactory()) {
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
    component.addNameChangedListener(onNameChangedListener)
  }

  fun switchBetweenCommunityAndFactoriesTables() {
    isPanelFromFactory = !isPanelFromFactory
    savePreferencesIfNeeded(isPanelFromFactory)

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

  companion object {
    val PY_DATA_VIEWER_PANEL_KEY: DataKey<PyDataViewerPanel> = DataKey.create("PY_DATA_VIEWER_PANEL_KEY")

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

    private fun isPanelsFromFactoryAvailable(): Boolean {
      return PyDataViewPanelFactory.EP_NAME.extensionList.isNotEmpty()
    }

    private fun isPanelFromFactory(): Boolean {
      if (!isPanelsFromFactoryAvailable()) return false
      if (Registry.`is`("python.data.view.allow.save.preferences.community.vs.powerful.data.view", false)) {
        return isUsingCommunityPanel()
      }
      return true
    }

    private fun savePreferencesIfNeeded(newValue: Boolean) {
      if (Registry.`is`("python.data.view.allow.save.preferences.community.vs.powerful.data.view", false)) {
        setUsingCommunityPanel(newValue)
      }
    }

    private const val DATA_VIEWER_SHOW_COMMUNITY_PANEL = "py.data.viewer.powerful.table.shown.on.each.data.view.opening"
    fun isUsingCommunityPanel(): Boolean = PropertiesComponent.getInstance().getBoolean(DATA_VIEWER_SHOW_COMMUNITY_PANEL, true)
    fun setUsingCommunityPanel(value: Boolean): Unit = PropertiesComponent.getInstance().setValue(DATA_VIEWER_SHOW_COMMUNITY_PANEL, value, true)
  }
}