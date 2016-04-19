package com.jetbrains.edu.learning.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.jetbrains.edu.learning.StudyProjectComponent
import com.jetbrains.edu.learning.StudyUtils
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener


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
    val JAVAFX_ITEM = "Panel with code highlighting"
    val SWING_ITEM = "Simple panel"
    private val myProject: Project
    private val myComboBox: ComboBox<String>


    constructor(project: Project, canBeParent: Boolean) : super(project, canBeParent) {
      myProject = project
      myComboBox = ComboBox<String>()
      val comboBoxModel = DefaultComboBoxModel<String>()
      val projectComponent = StudyProjectComponent.getInstance(project)
      
      if (StudyUtils.hasJavaFx()) {
        comboBoxModel.addElement(JAVAFX_ITEM)
      }
      comboBoxModel.addElement(SWING_ITEM)
      comboBoxModel.addListDataListener(object : ListDataListener {
        override fun contentsChanged(e: ListDataEvent?) {
          isOKActionEnabled = (projectComponent.useJavaFx() && myComboBox.selectedItem == SWING_ITEM) 
              || (!projectComponent.useJavaFx() && myComboBox.selectedItem == JAVAFX_ITEM)
        }

        override fun intervalRemoved(e: ListDataEvent?) {
        }

        override fun intervalAdded(e: ListDataEvent?) {
        }

      })
      comboBoxModel.selectedItem = if (projectComponent.useJavaFx()) JAVAFX_ITEM else SWING_ITEM
      myComboBox.model = comboBoxModel
      title = "Switch Task Description Panel"
      isOKActionEnabled = false
      myComboBox.setMinimumAndPreferredWidth(300)
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
      StudyProjectComponent.getInstance(myProject).setUseJavaFx(myComboBox.selectedItem == JAVAFX_ITEM)
    }
  }
}