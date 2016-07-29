package com.jetbrains.edu.learning.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.browsers.WebBrowserManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.FocusChangeListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.jetbrains.edu.learning.StudyTaskManager
import com.jetbrains.edu.learning.StudyUtils
import com.jetbrains.edu.learning.core.EduNames
import com.jetbrains.edu.learning.courseFormat.AnswerPlaceholder

class StudyHint(private val myPlaceholder: AnswerPlaceholder?, project: Project) {
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
          group.addAll(GoBackward(), GoForward(), Separator.getInstance(), EditHint(), AddHint(), RemoveHint())
        }
        studyToolWindow.setActionToolbar(group)
        if (!hints.isEmpty()) {
          studyToolWindow.setText(hints[myShownHintNumber])
        }
        else {
          studyToolWindow.setText("No hints are provided")
        }
      }
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

  private inner class EditHint : ToggleAction("Edit Hint", "Edit Hint", AllIcons.Modules.Edit) {

    private var currentDocument: Document? = null

    override fun isSelected(e: AnActionEvent): Boolean {
      e.project ?: return false
      return isEditingMode
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      val project = e.project ?: return
      e.presentation.text = if (state) "Save Hint" else "Edit Hint"
      doOnSelection(state, project)
    }

    fun doOnSelection(state: Boolean, project: Project) {
      if (state) {
        isEditingMode = true
        val factory = EditorFactory.getInstance()
        currentDocument = factory.createDocument(myPlaceholder!!.hints[myShownHintNumber])
        WebBrowserManager.getInstance().isShowBrowserHover = false
        if (currentDocument != null) {
          val createdEditor = factory.createEditor(currentDocument as Document, project) as EditorEx
          Disposer.register(project, Disposable { factory.releaseEditor(createdEditor) })
          val editorComponent = createdEditor.component
          studyToolWindow.setTopComponent(editorComponent)
          studyToolWindow.repaint()

          createdEditor.addFocusListener(object: FocusChangeListener {
            override fun focusGained(editor: Editor?) {
              if (createdEditor.document.text == newHintDefaultText) {
                ApplicationManager.getApplication().runWriteAction { createdEditor.document.setText("") }
              }
            }

            override fun focusLost(editor: Editor?) {
            }

          })
        }
      }
      else {
        isEditingMode = false
        myPlaceholder!!.setHintByIndex(myShownHintNumber, currentDocument!!.text)
        studyToolWindow.setText(myPlaceholder.hints[myShownHintNumber])
        studyToolWindow.setDefaultTopComponent()
      }
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
      e?.presentation?.isEnabled = !isEditingMode && myPlaceholder != null && !myPlaceholder.hints.isEmpty()
    }
  }

  private inner class RemoveHint : AnAction("Remove Hint", "Remove Hint", AllIcons.General.Remove) {

    override fun actionPerformed(e: AnActionEvent) {
      myPlaceholder!!.removeHint(myShownHintNumber)
      myShownHintNumber = if (myPlaceholder.hints.size == 1) 0 else if (myShownHintNumber + 1 < myPlaceholder.hints.size) myShownHintNumber + 1 else myShownHintNumber - 1
      studyToolWindow.setText(myPlaceholder.hints[myShownHintNumber])
    }

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = myPlaceholder != null && myPlaceholder.hints.size > 1 && !isEditingMode
    }
  }

  companion object {
    private val OUR_WARNING_MESSAGE = "Put the caret in the answer placeholder to get hint"
  }
}
