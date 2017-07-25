package training.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import training.learn.LearnBundle

/**
 * Created by karashevich on 11/01/16.
 */
class IndexingWarningDummyAction : AnAction(LearnBundle.message("action.IndexingWarningDummyAction.description")) {
  init {
    this.templatePresentation.isEnabled = false
  }

  override fun actionPerformed(anActionEvent: AnActionEvent) {
    //do nothing
  }

}
