package com.jetbrains.edu.coursecreator.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.jetbrains.edu.coursecreator.actions.placeholder.CCCreateAnswerPlaceholderDialog
import com.jetbrains.edu.learning.courseFormat.AnswerPlaceholder
import com.jetbrains.edu.learning.ui.StudyHint
import java.util.*

class CCHint(private val myPlaceholder: AnswerPlaceholder?, project: Project) : StudyHint(myPlaceholder, project) {

  private val newHintDefaultText = "Edit this hint"

  override fun getActions(): List<AnAction> {
    val result = ArrayList<AnAction>()
    result.add(GoBackward())
    result.add(GoForward())
    result.add(Separator.getInstance())
    result.add(EditHint())
    return result
  }

  private inner class EditHint : AnAction("Edit Hint", "Edit Hint", AllIcons.Modules.Edit) {

    override fun actionPerformed(e: AnActionEvent?) {
      val dlg = CCCreateAnswerPlaceholderDialog(e!!.project!!, myPlaceholder!!.taskText,
                                                                                                    myPlaceholder.hints)
      dlg.title = "Edit Answer Placeholder"
      if (dlg.showAndGet()) {
        val answerPlaceholderText = dlg.taskText
        myPlaceholder.taskText = answerPlaceholderText
        myPlaceholder.length = if (myPlaceholder.activeSubtaskInfo.isNeedInsertText) 0 else StringUtil.notNullize(answerPlaceholderText).length
        myPlaceholder.hints = dlg.hints
      }
    }

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = myPlaceholder != null
    }
  }
}
