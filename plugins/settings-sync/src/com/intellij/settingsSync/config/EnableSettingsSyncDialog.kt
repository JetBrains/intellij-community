package com.intellij.settingsSync.config

import com.intellij.codeInsight.hint.HintUtil
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.settingsSync.SettingsSyncBundle.message
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.JBColor
import com.intellij.ui.SideBorder
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.UIUtil.DEFAULT_HGAP
import org.jetbrains.annotations.Nls
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.SwingConstants
import javax.swing.border.Border

internal class EnableSettingsSyncDialog
  private constructor(parent: JComponent, private val remoteSettingsFound: Boolean) : DialogWrapper(parent, false) {

  private lateinit var configPanel: DialogPanel
  private var dialogResult: Result? = null

  init {
    title = message("title.settings.sync")
    init()
  }

  enum class Result {
    PUSH_LOCAL,
    GET_FROM_SERVER
  }

  companion object {
    fun showAndGetResult(parent: JComponent, remoteSettingsFound: Boolean) : Result? {
      val dialog = EnableSettingsSyncDialog(parent, remoteSettingsFound)
      dialog.show()
      return dialog.getResult()
    }
  }

  override fun createTitlePane(): JComponent? {
    val warningPanel =
      JBLabel(message("settings.cross.ide.sync.warning.label"), AllIcons.General.Information, SwingConstants.LEADING)
        .apply {
          background = EditorColorsManager.getInstance().globalScheme.getColor(HintUtil.PROMOTION_PANE_KEY)
          isOpaque = true
          border = JBUI.Borders.merge(JBUI.Borders.empty(DEFAULT_HGAP),
                                      IdeBorderFactory.createBorder(JBColor.border(), SideBorder.BOTTOM.or(SideBorder.TOP)), true)
        }
    return warningPanel
  }

  override fun createContentPaneBorder(): Border? {
    return null
  }

  override fun createCenterPanel(): JComponent {
    configPanel = SettingsSyncPanelFactory.createPanel(getHeader())
    configPanel.reset()

    val centerPanel = JBUI.Panels.simplePanel(configPanel)
    val defaultInsets = UIUtil.getRegularPanelInsets()
    centerPanel.border = JBEmptyBorder(defaultInsets.top, defaultInsets.left, 0, defaultInsets.right)
    return centerPanel
  }

  override fun createSouthPanel(): JComponent {
    val centerPanel = JBUI.Panels.simplePanel(super.createSouthPanel())
    val defaultInsets = UIUtil.getRegularPanelInsets()
    centerPanel.border = JBEmptyBorder(0, defaultInsets.left, defaultInsets.bottom, defaultInsets.right)
    return centerPanel
  }

  private fun getHeader(): @Nls String {
    return (if (remoteSettingsFound) message("enable.dialog.settings.found") + " " else "") + message("enable.dialog.select.what.to.sync")
  }

  override fun createActions(): Array<Action> =
    if (remoteSettingsFound) arrayOf(cancelAction, SyncLocalSettingsAction(), GetSettingsFromAccountAction())
    else {
      val enableSyncAction = EnableSyncAction()
      enableSyncAction.putValue(DEFAULT_ACTION, true)
      arrayOf(cancelAction, enableSyncAction)
    }

  inner class EnableSyncAction : AbstractAction(message("enable.dialog.enable.sync.action")) {
    override fun actionPerformed(e: ActionEvent?) {
      applyAndClose(Result.PUSH_LOCAL)
    }
  }

  inner class SyncLocalSettingsAction : AbstractAction(message("enable.dialog.sync.local.settings")) {
    override fun actionPerformed(e: ActionEvent?) {
      applyAndClose(Result.PUSH_LOCAL)
    }
  }

  inner class GetSettingsFromAccountAction : AbstractAction(message("enable.dialog.get.settings.from.account")) {
    override fun actionPerformed(e: ActionEvent?) {
      applyAndClose(Result.GET_FROM_SERVER)
    }
  }

  private fun applyAndClose(result: Result) {
    configPanel.apply()
    dialogResult = result
    close(0, true)
  }

  fun getResult(): Result? = dialogResult
}