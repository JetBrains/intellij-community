package training.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationNamesInfo
import training.learn.LearnBundle

/**
 * Created by karashevich on 11/01/16.
 */
class IndexingWarningDummyAction : AnAction(LearnBundle.message("action.IndexingWarningDummyAction.description", ApplicationNamesInfo.getInstance().getFullProductName())) {
  init {
    this.templatePresentation.isEnabled = false
  }

  override fun actionPerformed(anActionEvent: AnActionEvent) {
    //do nothing
  }

}
