// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.promotion.communityToUnified

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager
import com.intellij.pycharm.community.ide.impl.promotion.communityToUnified.statistics.PyCommunityUnifiedPromoFusCollector
import com.intellij.ui.GotItTooltip
import com.intellij.util.IconUtil
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.Nls
import java.awt.Point
import javax.swing.SwingUtilities

object PyCommunityToUnifiedTooltip {

  private const val TOOLTIP_ID: String = "pycharm.community.to.unified.tooltip.id"

  fun showTooltip(project: Project) {
    UIUtil.invokeLaterIfNeeded {
      val frame = WindowManager.getInstance().getIdeFrame(project) ?: return@invokeLaterIfNeeded
      val root = frame.component

      val actionId = "SettingsEntryPoint"
      val targetAction: AnAction? = ActionManager.getInstance().getAction(actionId)
      if (targetAction == null) return@invokeLaterIfNeeded

      val settingsButton: ActionButton? = UIUtil.uiTraverser(root)
        .filter(ActionButton::class.java)
        .firstOrNull { it.action == targetAction }


      if (settingsButton != null) {
        val tooltip = constructToolTip(project)
        PyCommunityUnifiedPromoFusCollector.TooltipShown.log()
        tooltip.show(settingsButton.rootPane.layeredPane) { pane, _ ->
          SwingUtilities.convertPoint(settingsButton,
                                      Point(settingsButton.width / 2, settingsButton.height), pane)
        }
      }
    }
  }

  private fun constructToolTip(project: Project): GotItTooltip {
    val image = IconUtil.scale(PyPromoSharedComponents.popUpImg, null, 0.7f)
    fun @Nls String.h3(): String = "<html><b><span style='font-size:1.17em'>$this</span></b></html>"

    return GotItTooltip(TOOLTIP_ID, PyPromoSharedComponents.mainText)
      .withImage(image, false)
      .withHeader(PyPromoSharedComponents.headerTitle.h3())
      .withButtonLabel(PyPromoSharedComponents.updateNow)
      .withLink(PyPromoSharedComponents.learnMore) {
        PyPromoSharedComponents.learnMoreBrowserAction.invoke()
      }
      .withSecondaryButton(PyPromoSharedComponents.skip) {
        PyCommunityUnifiedPromoFusCollector.TooltipClosed.log(PyCommunityUnifiedPromoFusCollector.TooltipCloseReason.DISMISSED)
        PyCommunityToUnifiedPromoService.getInstance().onRemindMeLaterClicked()
        PyCharmCommunityToUnifiedScheduleService.getInstance().scheduleFallbackModal()
      }
      .withGotItButtonAction {
        PyCommunityUnifiedPromoFusCollector.TooltipClosed.log(PyCommunityUnifiedPromoFusCollector.TooltipCloseReason.UPDATE_NOW)
        PyCommunityToUnifiedShowPromoActivity.Helper.launchUpdateDialog(project)
      }
      .withShowCount(Int.MAX_VALUE)
      .withFocus()
  }
}