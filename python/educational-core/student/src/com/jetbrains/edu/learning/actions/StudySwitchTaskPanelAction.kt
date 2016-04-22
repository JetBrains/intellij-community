package com.jetbrains.edu.learning.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.jetbrains.edu.learning.StudyTaskManager
import com.jetbrains.edu.learning.StudyUtils
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.JLabel


class StudySwitchTaskPanelAction: AnAction() {
  
  override fun actionPerformed(e: AnActionEvent?) {
    val project = e?.project
    if (project != null) {
      if (createDialog(project).showAndGet()) {
        StudyUtils.initToolWindows(project)
      }
    }
  }
  
  fun createDialog(project: Project): DialogWrapper {
    return MyDialog(project, false)
  }
  
  
  class MyDialog: DialogWrapper {
    val JAVAFX_ITEM = "JavaFX"
    val SWING_ITEM = "Swing"
    private val myProject: Project
    private val myComboBox: ComboBox<String>


    constructor(project: Project, canBeParent: Boolean) : super(project, canBeParent) {
      myProject = project
      myComboBox = ComboBox<String>()
      val comboBoxModel = DefaultComboBoxModel<String>()

      if (StudyUtils.hasJavaFx()) {
        comboBoxModel.addElement(JAVAFX_ITEM)
      }
      comboBoxModel.addElement(SWING_ITEM)
      
      comboBoxModel.selectedItem =
          if (StudyUtils.hasJavaFx() && StudyTaskManager.getInstance(project).shouldUseJavaFx()) JAVAFX_ITEM else SWING_ITEM
      myComboBox.model = comboBoxModel
      title = "Switch Task Description Panel"
      myComboBox.setMinimumAndPreferredWidth(250)
      init()
    }


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
      StudyTaskManager.getInstance(myProject).setShouldUseJavaFx(myComboBox.selectedItem == JAVAFX_ITEM)
    }
  }

  override fun update(e: AnActionEvent?) {
    val project = e?.project
    if (project != null && StudyUtils.isStudyProject(project)) {
      e?.presentation?.isEnabled = true
    }
    else {
      e?.presentation?.isEnabled = false
    }
  }
}