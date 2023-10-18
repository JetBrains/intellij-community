package com.intellij.settingsSync

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.newEditor.AbstractEditor
import com.intellij.openapi.options.newEditor.SettingsDialogListener
import com.intellij.openapi.options.newEditor.SettingsEditor
import com.intellij.openapi.options.newEditor.SettingsTreeView
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.GotItTooltip
import com.intellij.ui.treeStructure.SimpleNode
import com.intellij.ui.treeStructure.filtered.FilteringTreeStructure
import com.intellij.util.ui.tree.TreeUtil
import java.awt.Point
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath

class SettingsSyncPromotion : SettingsDialogListener {
  override fun afterApply(settingsEditor: AbstractEditor) {
    if (settingsEditor !is SettingsEditor
        || SettingsSyncSettings.getInstance().syncEnabled
        || !Registry.`is`("settingsSync.promotion.in.settings", false)) {
      return
    }

    val settingsTree = settingsEditor.treeView.tree
    val settingsSyncPath = TreeUtil.treePathTraverser(settingsTree).find { path ->
      val configurable = getConfigurable(path)
      configurable?.displayName == SettingsSyncBundle.message("title.settings.sync")
    } ?: return
    val settingsSyncConfigurable = getConfigurable(settingsSyncPath)!!

    settingsTree.scrollPathToVisible(settingsSyncPath)

    GotItTooltip("settings.sync.in.settings",
                 SettingsSyncBundle.message("promotion.in.settings.text"),
                 parentDisposable = settingsEditor)
      .withHeader(SettingsSyncBundle.message("promotion.in.settings.header"))
      .withButtonLabel(SettingsSyncBundle.message("promotion.in.settings.open"))
      .withSecondaryButton(SettingsSyncBundle.message("promotion.in.settings.skip"))
      .withGotItButtonAction {
        invokeLater(ModalityState.stateForComponent(settingsEditor)) {
          settingsEditor.select(settingsSyncConfigurable)
        }
      }
      .withPosition(Balloon.Position.atRight)
      .show(settingsTree) { _, _ ->
        val pathBounds = settingsTree.getPathBounds(settingsSyncPath) ?: error("Failed to get bounds for path: $settingsSyncPath")
        Point(pathBounds.x + pathBounds.width, pathBounds.y + pathBounds.height / 2)
      }
  }

  private fun getConfigurable(path: TreePath): Configurable? {
    val lastNode = path.lastPathComponent as? DefaultMutableTreeNode ?: return null
    val filteringNode = lastNode.userObject as? FilteringTreeStructure.FilteringNode ?: return null
    val delegate = filteringNode.delegate as? SimpleNode ?: return null
    return SettingsTreeView.getConfigurable(delegate)
  }
}