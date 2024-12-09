package com.intellij.settingsSync

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.newEditor.AbstractEditor
import com.intellij.openapi.options.newEditor.SettingsDialogListener
import com.intellij.openapi.options.newEditor.SettingsEditor
import com.intellij.openapi.options.newEditor.SettingsTreeView
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.settingsSync.auth.SettingsSyncAuthService
import com.intellij.settingsSync.statistics.SettingsSyncEventsStatistics
import com.intellij.settingsSync.statistics.SettingsSyncEventsStatistics.PromotionInSettingsEvent
import com.intellij.ui.GotItTooltip
import com.intellij.ui.treeStructure.SimpleNode
import com.intellij.ui.treeStructure.filtered.FilteringTreeStructure
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.tree.TreeUtil
import java.awt.Point
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath
import kotlin.math.min

class SettingsSyncPromotion : SettingsDialogListener {
  override fun afterApply(settingsEditor: AbstractEditor) {
    if (settingsEditor !is SettingsEditor
        || SettingsSyncSettings.getInstance().syncEnabled
        || SettingsSyncLocalSettings.getInstance().knownAndAppliedServerId != null
        || !Registry.`is`("settingsSync.promotion.in.settings", false)) {
      return
    }

    val gotItTooltip = GotItTooltip("settings.sync.in.settings",
                                    SettingsSyncBundle.message("promotion.in.settings.text"),
                                    parentDisposable = settingsEditor)
    if (!gotItTooltip.canShow()) {
      Disposer.dispose(gotItTooltip)
      return  // It was already shown once
    }
    // mark it as shown, to not show it again, not depending on the way how it is closed
    Disposer.register(gotItTooltip) { gotItTooltip.gotIt() }

    promotionShownThisSession = true
    val settingsTree = settingsEditor.treeView.tree
    val settingsSyncPath = TreeUtil.treePathTraverser(settingsTree).find { path ->
      val configurable = getConfigurable(path)
      configurable?.displayName == SettingsSyncBundle.message("title.settings.sync")
    } ?: return
    val settingsSyncConfigurable = getConfigurable(settingsSyncPath)!!

    settingsTree.scrollPathToVisible(settingsSyncPath)

    gotItTooltip
      .withHeader(SettingsSyncBundle.message("promotion.in.settings.header"))
      .withButtonLabel(SettingsSyncBundle.message("promotion.in.settings.open"))
      .withSecondaryButton(SettingsSyncBundle.message("promotion.in.settings.skip")) {
        SettingsSyncEventsStatistics.PROMOTION_IN_SETTINGS.log(PromotionInSettingsEvent.SKIP)
      }
      .withGotItButtonAction {
        invokeLater(ModalityState.stateForComponent(settingsEditor)) {
          settingsEditor.select(settingsSyncConfigurable)
        }
        SettingsSyncEventsStatistics.PROMOTION_IN_SETTINGS.log(PromotionInSettingsEvent.GO_TO_SETTINGS_SYNC)
      }
      .withPosition(Balloon.Position.atRight)
      .show(settingsTree) { _, _ ->
        val pathBounds = settingsTree.getPathBounds(settingsSyncPath) ?: error("Failed to get bounds for path: $settingsSyncPath")
        val x = pathBounds.x + min(pathBounds.width, JBUI.scale(150))
        Point(x, pathBounds.y + pathBounds.height / 2)
      }

    SettingsSyncEventsStatistics.PROMOTION_IN_SETTINGS.log(PromotionInSettingsEvent.SHOWN)

    SettingsSyncEvents.getInstance().addListener(object : SettingsSyncEventListener {
      override fun loginStateChanged() {
        if (SettingsSyncAuthService.getInstance().isLoggedIn()) {
          SettingsSyncEventsStatistics.PROMOTION_IN_SETTINGS.log(PromotionInSettingsEvent.LOGGED_IN)
        }
      }

      override fun enabledStateChanged(syncEnabled: Boolean) {
        if (syncEnabled) {
          SettingsSyncEventsStatistics.PROMOTION_IN_SETTINGS.log(PromotionInSettingsEvent.ENABLED)
          SettingsSyncEvents.getInstance().removeListener(this)
        }
      }
    }, parentDisposable = settingsEditor)
  }

  private fun getConfigurable(path: TreePath): Configurable? {
    val lastNode = path.lastPathComponent as? DefaultMutableTreeNode ?: return null
    val filteringNode = lastNode.userObject as? FilteringTreeStructure.FilteringNode ?: return null
    val delegate = filteringNode.delegate as? SimpleNode ?: return null
    return SettingsTreeView.getConfigurable(delegate)
  }

  companion object {
    var promotionShownThisSession = false
  }
}