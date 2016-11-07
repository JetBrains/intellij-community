package com.jetbrains.edu.learning.editor

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.ProjectUtil
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotifications
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBRadioButton
import com.jetbrains.edu.learning.StudyUtils
import com.jetbrains.edu.learning.courseFormat.Task
import java.util.*
import javax.swing.ButtonGroup
import javax.swing.JPanel
import javax.swing.JToggleButton

class StudyChoiceTaskEditorNotificationsProvider : EditorNotifications.Provider<JPanel>() {
  override fun getKey(): Key<JPanel> {
    return KEY
  }

  override fun createNotificationPanel(file: VirtualFile, fileEditor: FileEditor): JPanel? {
    val project = ProjectUtil.guessProjectForContentFile(file) ?: return null

    val task = StudyUtils.getTask(project, file.parent)
    if (task != null && !task.choiceVariants.isEmpty()) {
      return task.let(::ChoicePanel)
    }

    return null
  }

  companion object {
    private val KEY = Key.create<JPanel>("edu.choicetask")
  }
}

class ChoicePanel(task: Task): JPanel(VerticalFlowLayout()) {
  val buttons: ArrayList<JToggleButton> = ArrayList()

  init {
    if (task.isMultichoice) {
      for ((index, variant) in task.choiceVariants.withIndex()) {
        val button: JBCheckBox = JBCheckBox(variant)
        button.addItemListener { task.choiceAnswer[index] = button.isSelected }
        buttons.add(button)
        add(button)
      }
    }
    else {
      val buttonGroup = ButtonGroup()

      for ((index, variant) in task.choiceVariants.withIndex()) {
        val button: JBRadioButton = JBRadioButton(variant)
        button.isFocusable = false
        button.addItemListener { task.choiceAnswer[index] = button.isSelected }
        buttons.add(button)
        buttonGroup.add(button)
        add(button)
      }
    }
  }
}
