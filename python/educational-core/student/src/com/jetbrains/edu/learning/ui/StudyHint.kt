package com.jetbrains.edu.learning.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.browsers.WebBrowserManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.jetbrains.edu.learning.StudyTaskManager
import com.jetbrains.edu.learning.StudyUtils
import com.jetbrains.edu.learning.core.EduNames
import com.jetbrains.edu.learning.courseFormat.AnswerPlaceholder
import java.util.*

class StudyHint(private val myPlaceholder: AnswerPlaceholder?, project: Project) {
  val studyToolWindow: StudyToolWindow
  private val myHints = LinkedList<String>()
  private var myShownHintNumber = 0
  private var isEditingMode = false

  init {
    if (myPlaceholder == null) {
      myHints.add(OUR_WARNING_MESSAGE)
    }
    else {
      myHints.addAll(myPlaceholder.hints)
    }
    val taskManager = StudyTaskManager.getInstance(project)
    if (StudyUtils.hasJavaFx() && taskManager.shouldUseJavaFx()) {
      studyToolWindow = StudyJavaFxToolWindow()
    }
    else {
      studyToolWindow = StudySwingToolWindow()
    }
    studyToolWindow.init(project, false)
    val course = taskManager.course
    if (course != null) {
      val courseMode = course.courseMode
      val group = DefaultActionGroup()
      group.addAll(GoBackward(), GoForward())
      if (EduNames.STUDY != courseMode) {
        group.addAll(Separator.getInstance(), EditHint(), AddHint(), RemoveHint())
      }
      studyToolWindow.setActionToolbar(group)
      if (!myHints.isEmpty()) {
        studyToolWindow.setText(myHints[myShownHintNumber])
      }
      else {
        studyToolWindow.setText("No hints are provided")
      }
    }
  }

  private inner class GoForward : AnAction(AllIcons.Actions.Forward) {

    override fun actionPerformed(e: AnActionEvent) {
      studyToolWindow.setText(myHints[++myShownHintNumber])
    }

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = !isEditingMode && myShownHintNumber + 1 < myHints.size
    }
  }

  private inner class GoBackward : AnAction(AllIcons.Actions.Back) {

    override fun actionPerformed(e: AnActionEvent) {
      studyToolWindow.setText(myHints[--myShownHintNumber])
    }

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = !isEditingMode && myShownHintNumber - 1 >= 0
    }
  }

  private inner class EditHint : ToggleAction("Edit Hint", "Edit Hint", AllIcons.Modules.Edit) {

    private var currentDocument: Document? = null

    override fun isSelected(e: AnActionEvent): Boolean {
      e.project ?: return false
      return isEditingMode
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      val project = e.project ?: return

      doOnSelection(state, project)
    }

    fun doOnSelection(state: Boolean, project: Project) {
      if (state) {
        isEditingMode = true
        val factory = EditorFactory.getInstance()
        currentDocument = factory.createDocument(myHints[myShownHintNumber])
        WebBrowserManager.getInstance().isShowBrowserHover = false
        if (currentDocument != null) {
          val createdEditor = factory.createEditor(currentDocument as Document, project) as EditorEx
          Disposer.register(project, Disposable { factory.releaseEditor(createdEditor) })
          val editorComponent = createdEditor.component
          studyToolWindow.setTopComponent(editorComponent)
          studyToolWindow.repaint()
        }
      }
      else {
        isEditingMode = false
        myHints[myShownHintNumber] = currentDocument!!.text
        val hints = myPlaceholder!!.hints
        hints[myShownHintNumber] = currentDocument!!.text
        studyToolWindow.setText(myHints[myShownHintNumber])
        studyToolWindow.setDefaultTopComponent()
      }
    }

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = !myHints.isEmpty() && myPlaceholder != null
    }
  }

  private inner class AddHint : AnAction(AllIcons.General.Add) {

    override fun actionPerformed(e: AnActionEvent) {
      val project = e.project ?: return
      val newHint = "New hint"
      myHints.add(newHint)
      myPlaceholder!!.hints.add(newHint)
      myShownHintNumber++
      studyToolWindow.setText(newHint)
      val actions = studyToolWindow.getActions(true)
      for (action in actions) {
        if (action is EditHint) {
          action.doOnSelection(true, project)
          action.isSelected(e)
          return
        }
      }
    }

    override fun update(e: AnActionEvent?) {
      e?.presentation?.isEnabled = !isEditingMode && !myHints.isEmpty()
    }
  }

  private inner class RemoveHint : AnAction(AllIcons.Actions.Cancel) {

    override fun actionPerformed(e: AnActionEvent) {
      myHints.removeAt(myShownHintNumber)
      myPlaceholder!!.hints.removeAt(myShownHintNumber)
      myShownHintNumber = if (myHints.size == 1) 0 else if (myShownHintNumber + 1 < myHints.size) myShownHintNumber + 1 else myShownHintNumber - 1
      studyToolWindow.setText(myHints[myShownHintNumber])
    }

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = myHints.size > 1 && !isEditingMode
    }
  }

  companion object {

    private val OUR_WARNING_MESSAGE = "Put the caret in the answer placeholder to get hint"
  }
}
