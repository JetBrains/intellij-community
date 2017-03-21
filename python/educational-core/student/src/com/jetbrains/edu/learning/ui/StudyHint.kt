package com.jetbrains.edu.learning.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.jetbrains.edu.learning.StudyTaskManager
import com.jetbrains.edu.learning.StudyUtils
import com.jetbrains.edu.learning.courseFormat.AnswerPlaceholder
import java.util.*

open class StudyHint(private val myPlaceholder: AnswerPlaceholder?,
                     project: Project) {
  
  companion object {
    private val OUR_WARNING_MESSAGE = "Put the caret in the answer placeholder to get hint"
    private val HINTS_NOT_AVAILABLE = "There is no hint for this answer placeholder"
  }
  
  val studyToolWindow: StudyToolWindow
  protected var myShownHintNumber = 0
  protected var isEditingMode = false

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
      val group = DefaultActionGroup()
      val hints = myPlaceholder?.hints
      if (hints != null) {
        if (hints.size > 1) {
          group.addAll(getActions())
        }
        studyToolWindow.setActionToolbar(group)
        setHintText(hints)
      }
    }
  }

  protected open fun getActions(): List<AnAction> {
    val result = ArrayList<AnAction>()
    result.add(GoBackward())
    result.add(GoForward())
    return result
  }

  protected fun setHintText(hints: List<String>) {
    if (!hints.isEmpty()) {
      studyToolWindow.setText(hints[myShownHintNumber])
    }
    else {
      myShownHintNumber = -1
      studyToolWindow.setText(HINTS_NOT_AVAILABLE)
    }
  }

  inner class GoForward : AnAction("Next Hint", "Next Hint", AllIcons.Actions.Forward) {


    override fun actionPerformed(e: AnActionEvent) {
      studyToolWindow.setText(myPlaceholder!!.hints[++myShownHintNumber])
    }

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = !isEditingMode && myPlaceholder != null && myShownHintNumber + 1 < myPlaceholder.hints.size
    }
  }

  inner class GoBackward : AnAction("Previous Hint", "Previous Hint", AllIcons.Actions.Back) {

    override fun actionPerformed(e: AnActionEvent) {
      studyToolWindow.setText(myPlaceholder!!.hints[--myShownHintNumber])
    }

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = !isEditingMode && myShownHintNumber - 1 >= 0
    }
  }
}
