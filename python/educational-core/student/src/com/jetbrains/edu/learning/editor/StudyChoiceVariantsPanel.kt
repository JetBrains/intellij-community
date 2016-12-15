package com.jetbrains.edu.learning.editor

import com.intellij.openapi.editor.colors.EditorColorsManager
import com.jetbrains.edu.learning.courseFormat.Task
import javafx.application.Platform
import javafx.beans.value.ObservableValue
import javafx.embed.swing.JFXPanel
import javafx.geometry.Insets
import javafx.scene.Group
import javafx.scene.Scene
import javafx.scene.control.ButtonBase
import javafx.scene.control.CheckBox
import javafx.scene.control.RadioButton
import javafx.scene.control.ToggleGroup
import javafx.scene.layout.VBox
import javafx.scene.text.Font
import javax.swing.JScrollPane
import javax.swing.ScrollPaneConstants


class StudyChoiceVariantsPanel(task: Task) : JScrollPane() {

  private val LEFT_INSET = 15.0
  private val RIGHT_INSET = 10.0
  private val TOP_INSET = 15.0
  private val BOTTOM_INSET = 10.0

  init {
    val jfxPanel = JFXPanel()
    Platform.runLater {
      val group = Group()
      val scene = Scene(group)
      jfxPanel.scene = scene
      val vBox = VBox()
      vBox.spacing = 10.0
      vBox.padding = Insets(TOP_INSET, RIGHT_INSET, BOTTOM_INSET, LEFT_INSET)
      if (task.isMultipleChoice) {
        for ((index, variant) in task.choiceVariants.withIndex()) {
          val isSelected = task.selectedVariants.contains(index)
          val checkBox = CheckBox(variant)
          checkBox.isSelected = isSelected
          checkBox.selectedProperty().addListener(createSelectionListener(task, index))
          setUpButtonStyle(checkBox, scene)
          vBox.children.add(checkBox)
        }
      }
      else {
        val toggleGroup = ToggleGroup()
        for ((index, variant) in task.choiceVariants.withIndex()) {
          val isSelected = task.selectedVariants.contains(index)
          val radioButton = RadioButton(variant)
          radioButton.toggleGroup = toggleGroup
          radioButton.isSelected = isSelected
          radioButton.selectedProperty().addListener(createSelectionListener(task, index))
          setUpButtonStyle(radioButton, scene)
          vBox.children.add(radioButton)
        }
      }

      group.children.add(vBox)
    }
    setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER)
    setViewportView(jfxPanel)
  }

  private fun createSelectionListener(task: Task, index: Int): (ObservableValue<out Boolean>, Boolean, Boolean) -> Unit {
    return { observableValue, wasSelected, isSelected ->
      if (isSelected) {
        task.selectedVariants.add(index)
      }
      else {
        task.selectedVariants.remove(index)
      }
    }
  }

  private fun setUpButtonStyle(button: ButtonBase, scene: Scene) {
    button.isWrapText = true
    button.maxWidthProperty().bind(scene.widthProperty().subtract(LEFT_INSET).subtract(RIGHT_INSET))
    button.font = Font.font((EditorColorsManager.getInstance().globalScheme.editorFontSize + 2).toDouble())
    button.stylesheets.add(javaClass.getResource("/style/buttons.css").toExternalForm())
  }
}
