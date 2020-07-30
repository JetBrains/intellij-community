package circlet.actions

import circlet.components.space
import circlet.settings.CircletSettingsPanel
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

class CircletLoginAction : DumbAwareAction() {
  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = space.workspace.value == null
    CircletActionUtils.showIconInActionSearch(e)
  }

  override fun actionPerformed(e: AnActionEvent) {
    CircletSettingsPanel.openSettings(e.project)
  }
}
