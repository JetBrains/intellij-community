package com.jetbrains.edu.learning.editor

import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.LafManagerListener
import com.intellij.ide.ui.laf.darcula.DarculaLookAndFeelInfo
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.util.ui.UIUtil
import com.jetbrains.edu.learning.courseFormat.tasks.ChoiceTask
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
import javafx.scene.paint.Color
import javafx.scene.text.Font
import java.util.*
import javax.swing.JScrollPane
import javax.swing.ScrollPaneConstants


class StudyChoiceVariantsPanel(task: ChoiceTask) : JScrollPane() {

  private val LEFT_INSET = 15.0
  private val RIGHT_INSET = 10.0
  private val TOP_INSET = 15.0
  private val BOTTOM_INSET = 10.0

  private val buttons: ArrayList<ButtonBase> = ArrayList()

  init {
    val jfxPanel = JFXPanel()
    LafManager.getInstance().addLafManagerListener(StudyLafManagerListener(jfxPanel))
    Platform.runLater {
      val group = Group()
      val scene = Scene(group, getSceneBackground())
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
          buttons.add(checkBox)
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
          buttons.add(radioButton)
        }
      }

      group.children.add(vBox)
    }
    setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER)
    setViewportView(jfxPanel)
  }

  private fun getSceneBackground(): Color {
    val isDarcula = LafManager.getInstance().currentLookAndFeel is DarculaLookAndFeelInfo
    val panelBackground = if (isDarcula) UIUtil.getPanelBackground() else UIUtil.getTextFieldBackground()
    return Color.rgb(panelBackground.red, panelBackground.green, panelBackground.blue)
  }

  private fun createSelectionListener(task: ChoiceTask, index: Int): (ObservableValue<out Boolean>, Boolean, Boolean) -> Unit {
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

    setButtonLaf(button)
  }

  private fun setButtonLaf(button: ButtonBase) {
    val darcula = LafManager.getInstance().currentLookAndFeel is DarculaLookAndFeelInfo
    val stylesheetPath = if (darcula) "/style/buttonsDarcula.css" else "/style/buttons.css"
    button.stylesheets.removeAll()
    button.stylesheets.add(javaClass.getResource(stylesheetPath).toExternalForm())
  }

  private inner class StudyLafManagerListener(val jfxPanel: JFXPanel) : LafManagerListener {
    override fun lookAndFeelChanged(manager: LafManager) {
      Platform.runLater {
        val panelBackground = UIUtil.getPanelBackground()
        jfxPanel.scene.fill = Color.rgb(panelBackground.red, panelBackground.green, panelBackground.blue)
        for (button in buttons) {
          setButtonLaf(button)
        }
        jfxPanel.repaint()
      }
    }
  }
}
