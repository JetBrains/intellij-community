// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger.containerview

import com.intellij.icons.AllIcons
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.GotItTooltip
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.util.preferredHeight
import com.intellij.ui.util.preferredWidth
import com.jetbrains.python.PyBundle
import java.awt.Point

internal object PyDataViewerPanelHelper {
  fun showCommunityDataViewerRestrictionsBalloon(tablePanel: PyDataViewerAbstractPanel, tableParentPanel: PyDataViewerPanel) {
    createBalloon(tablePanel)
      .show(
        RelativePoint(
          tableParentPanel,
          Point(tableParentPanel.width - (tablePanel.topToolbar?.preferredWidth ?: 10) / 2, tablePanel.topToolbar?.preferredHeight ?: 0)
        ),
        Balloon.Position.below
      )
  }

  private fun createBalloon(component: PyDataViewerAbstractPanel): Balloon {
    return JBPopupFactory.getInstance()
      .createHtmlTextBalloonBuilder(PyBundle.message("debugger.dataViewer.dataframes.unsupported"), MessageType.INFO, null).apply {
        setDisposable(component)
        setHideOnAction(true)
        setHideOnClickOutside(true)
      }.createBalloon()
  }

  /**
   * GotIt Tooltip on the button with advertising of new and old table modes for viewing arrays in debug.
   */
  fun createGotItTooltip(tablePanel: PyDataViewerAbstractPanel, tableParentPanel: PyDataViewerPanel) {
    val tooltip = GotItTooltip("py.data.view.new.table",
                               PyBundle.message("debugger.dataViewer.switch.between.tables.gotIt.text"),
                               tableParentPanel)
      .withLink(PyBundle.message("debugger.dataViewer.switch.between.tables.gotIt.link")) { tableParentPanel.switchBetweenCommunityAndFactoriesTables() }
      .withShowCount(1)
      .withIcon(AllIcons.General.BalloonInformation)

    tooltip.show(tablePanel.topToolbar!!) { component, _ ->
      Point(component.width - component.height / 2, component.height)
    }
  }
}