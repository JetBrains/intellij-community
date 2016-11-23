package com.jetbrains.edu.learning.editor

import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotifications
import com.jetbrains.edu.learning.courseFormat.Task
import javafx.application.Platform
import javafx.embed.swing.JFXPanel
import javafx.geometry.Insets
import javafx.scene.Group
import javafx.scene.Scene
import javafx.scene.control.CheckBox
import javafx.scene.control.RadioButton
import javafx.scene.control.ToggleGroup
import javafx.scene.layout.VBox
import javafx.scene.text.Font
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.ScrollPaneConstants

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

  init {
    val jfxPanel = JFXPanel()
    Platform.runLater {
      val group = Group()
      val scene = Scene(group)
      jfxPanel.scene = scene
      val vBox = VBox()
      vBox.spacing = 10.0
      vBox.padding = Insets(15.0, 10.0, 10.0, 15.0)
      if (task.isMultipleChoice) {
        for ((index, variant) in task.choiceVariants.withIndex()) {
          val checkBox = CheckBox(variant)
          checkBox.font = Font.font((EditorColorsManager.getInstance().globalScheme.editorFontSize + 2).toDouble())
          checkBox.stylesheets.add(String::class.java.getResource("/style/buttons.css").toExternalForm())
          checkBox.selectedProperty().addListener { observableValue, wasSelected, isSelected ->
            if (isSelected) {
              task.selectedVariants.add(index)
            }
            else {
              task.selectedVariants.remove(index)
            }
          }
          vBox.children.add(checkBox)
        }
      }
      else {
        val toggleGroup = ToggleGroup()
        for ((index, variant) in task.choiceVariants.withIndex()) {
          val radioButton = RadioButton(variant)
          radioButton.font = Font.font((EditorColorsManager.getInstance().globalScheme.editorFontSize + 2).toDouble())
          radioButton.stylesheets.add(String::class.java.getResource("/style/buttons.css").toExternalForm())
          radioButton.toggleGroup = toggleGroup
          radioButton.selectedProperty().addListener { observableValue, wasSelected, isSelected ->
            if (isSelected) {
              task.selectedVariants.add(index)
            }
            else {
              task.selectedVariants.remove(index)
            }
          }
          vBox.children.add(radioButton)
        }
      }

      group.children.add(vBox)
    }
    setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER)
    setViewportView(jfxPanel)
  }
}
