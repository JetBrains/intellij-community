package com.jetbrains.edu.learning.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.project.Project
import com.jetbrains.edu.learning.StudyTaskManager
import com.jetbrains.edu.learning.StudyUtils
import com.jetbrains.edu.learning.core.EduNames
import com.jetbrains.edu.learning.courseFormat.AnswerPlaceholder

class StudyHint(private val myPlaceholder: AnswerPlaceholder?, 
                project: Project) {
  
  companion object {
    private val OUR_WARNING_MESSAGE = "Put the caret in the answer placeholder to get hint"
    private val HINTS_NOT_AVAILABLE = "There is no hint for this answer placeholder"
  }
  
  val studyToolWindow: StudyToolWindow
  private var myShownHintNumber = 0
  private var isEditingMode = false
  private val newHintDefaultText = "Edit this hint"

  init {
    val taskManager = StudyTaskManager.getInstance(project)
    if (StudyUtils.hasJavaFx() && taskManager.shouldUseJavaFx()) {
      studyToolWindow = StudyJavaFxToolWindow()
    }
    else {
      studyToolWindow = StudySwingToolWindow()
    }
    studyToolWindow.init(project, false)
    
    if (myPlaceholder == null) {
      studyToolWindow.setText(OUR_WARNING_MESSAGE)
      studyToolWindow.setActionToolbar(DefaultActionGroup())
    }
    
    val course = taskManager.course
    if (course != null) {
      val courseMode = course.courseMode
      val group = DefaultActionGroup()
      val hints = myPlaceholder?.hints
      if (hints != null) {
        if (courseMode == EduNames.STUDY) {
          if (hints.size > 1) {
            group.addAll(GoBackward(), GoForward())
          }
        }
        else {
          group.addAll(GoBackward(), GoForward(), Separator.getInstance(), EditHint())
        }
        studyToolWindow.setActionToolbar(group)
        setHintText(hints)
      }
    }
  }

  private fun setHintText(hints: List<String>) {
    if (!hints.isEmpty()) {
      studyToolWindow.setText(hints[myShownHintNumber])
    }
    else {
      myShownHintNumber = -1
      studyToolWindow.setText(HINTS_NOT_AVAILABLE)
    }
  }

  private inner class GoForward : AnAction("Next Hint", "Next Hint", AllIcons.Actions.Forward) {


    override fun actionPerformed(e: AnActionEvent) {
      studyToolWindow.setText(myPlaceholder!!.hints[++myShownHintNumber])
    }

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = !isEditingMode && myPlaceholder != null && myShownHintNumber + 1 < myPlaceholder.hints.size
    }
  }

  private inner class GoBackward : AnAction("Previous Hint", "Previous Hint", AllIcons.Actions.Back) {

    override fun actionPerformed(e: AnActionEvent) {
      studyToolWindow.setText(myPlaceholder!!.hints[--myShownHintNumber])
    }

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = !isEditingMode && myShownHintNumber - 1 >= 0
    }
  }

  private inner class EditHint : AnAction("Edit Hint", "Edit Hint", AllIcons.Modules.Edit) {
    
    override fun actionPerformed(e: AnActionEvent?) {
      val dialog = CCCreateAnswerPlaceholderDialog(e!!.project!!, myPlaceholder!!)
      dialog.show()
    }

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = myPlaceholder?.hints?.isEmpty() == false
    }
  }

  private inner class AddHint : AnAction("Add Hint", "Add Hint", AllIcons.General.Add) {

    override fun actionPerformed(e: AnActionEvent) {
      myPlaceholder!!.addHint(newHintDefaultText)
      myShownHintNumber++
      studyToolWindow.setText(newHintDefaultText)
    }

    override fun update(e: AnActionEvent?) {
      e?.presentation?.isEnabled = !isEditingMode && myPlaceholder != null
    }
  }

  private inner class RemoveHint : AnAction("Remove Hint", "Remove Hint", AllIcons.General.Remove) {

    override fun actionPerformed(e: AnActionEvent) {
      myPlaceholder!!.removeHint(myShownHintNumber)
      myShownHintNumber += if (myShownHintNumber < myPlaceholder.hints.size) 0 else -1
      
      setHintText(myPlaceholder.hints)
    }

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = myPlaceholder != null && myPlaceholder.hints.size > 0 && !isEditingMode
    }
  }
}
