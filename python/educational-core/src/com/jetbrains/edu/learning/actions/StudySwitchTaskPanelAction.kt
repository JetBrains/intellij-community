package com.jetbrains.edu.learning.actions

import com.intellij.openapi.actionSystem.ActionPlaces.ACTION_SEARCH
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.jetbrains.edu.learning.StudySettings
import com.jetbrains.edu.learning.StudyUtils
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.JLabel


class StudySwitchTaskPanelAction: AnAction() {
  
  override fun actionPerformed(e: AnActionEvent?) {
    val project = e?.project
    val result = createDialog().showAndGet()
    if (result && project != null) {
      StudyUtils.initToolWindows(project)
    }
  }
  
  fun createDialog(): DialogWrapper {
    return MyDialog(false)
  }

  class MyDialog(canBeParent: Boolean) : DialogWrapper(null, canBeParent) {
    val JAVAFX_ITEM = "JavaFX"
    val SWING_ITEM = "Swing"
    private val myComboBox: ComboBox<String> = ComboBox()

    override fun createCenterPanel(): JComponent? {
      return myComboBox
    }

    override fun createNorthPanel(): JComponent? {
      return JLabel("Choose panel: ")
    }

    override fun getPreferredFocusedComponent(): JComponent? {
      return myComboBox
    }

    override fun doOKAction() {
      super.doOKAction()
      StudySettings.getInstance().setShouldUseJavaFx(myComboBox.selectedItem == JAVAFX_ITEM)
    }

    init {
      val comboBoxModel = DefaultComboBoxModel<String>()
      if (StudyUtils.hasJavaFx()) {
        comboBoxModel.addElement(JAVAFX_ITEM)
      }
      comboBoxModel.addElement(SWING_ITEM)
      comboBoxModel.selectedItem =
          if (StudyUtils.hasJavaFx() && StudySettings.getInstance().shouldUseJavaFx()) JAVAFX_ITEM else SWING_ITEM
      myComboBox.model = comboBoxModel
      title = "Switch Task Description Panel"
      myComboBox.setMinimumAndPreferredWidth(250)
      init()
    }
  }

  override fun update(e: AnActionEvent?) {
    val place = e?.place
    val project = e?.project
    e?.presentation?.isEnabled = project != null && StudyUtils.isStudyProject(project) || ACTION_SEARCH == place
  }
}