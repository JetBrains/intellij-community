package circlet.actions

import circlet.components.space
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

class CircletLogoutAction : DumbAwareAction() {
  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = space.workspace.value != null
    CircletActionUtils.showIconInActionSearch(e)
  }

  override fun actionPerformed(e: AnActionEvent) {
    space.signOut()
  }
}
