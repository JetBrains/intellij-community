package com.jetbrains.edu.learning.editor

import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotifications
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBRadioButton
import com.intellij.util.ui.UIUtil
import com.jetbrains.edu.learning.courseFormat.Task
import java.awt.Font
import java.util.*
import javax.swing.*

class StudyChoiceTaskEditorNotificationsProvider : EditorNotifications.Provider<JPanel>() {
  override fun getKey(): Key<JPanel> {
    return KEY
  }

  override fun createNotificationPanel(file: VirtualFile, fileEditor: FileEditor): JPanel? {
//    val project = ProjectUtil.guessProjectForContentFile(file) ?: return null
//
//    val task = StudyUtils.getTask(project, file.parent)
//    if (task != null && !task.choiceVariants.isEmpty()) {
//      return task.let(::ChoicePanel)
//    }

    return null
  }

  companion object {
    private val KEY = Key.create<JPanel>("edu.choicetask")
  }
}

class ChoicePanel(task: Task): JScrollPane() {
  val buttons: ArrayList<JToggleButton> = ArrayList()

  init {
    val jPanel = JPanel(VerticalFlowLayout())
    jPanel.background = UIUtil.getEditorPaneBackground()
    if (task.isMultichoice) {
      for ((index, variant) in task.choiceVariants.withIndex()) {
        val button: JBCheckBox = JBCheckBox(variant)
        button.background = UIUtil.getEditorPaneBackground()
        button.font = Font(button.font.name, button.font.style, EditorColorsManager.getInstance().globalScheme.editorFontSize + 2)
        button.addItemListener { task.choiceAnswer[index] = button.isSelected }
        buttons.add(button)
        jPanel.add(button)
      }
    }
    else {
      val buttonGroup = ButtonGroup()

      for ((index, variant) in task.choiceVariants.withIndex()) {
        val button: JBRadioButton = JBRadioButton(variant)
        button.font = Font(button.font.name, button.font.style, EditorColorsManager.getInstance().globalScheme.editorFontSize + 2)
        button.isFocusable = false
        button.background = UIUtil.getEditorPaneBackground()
        button.addItemListener { task.choiceAnswer[index] = button.isSelected }
        buttons.add(button)
        buttonGroup.add(button)
        jPanel.add(button)
      }
    }
    setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER)
    setViewportView(jPanel)
  }
}
