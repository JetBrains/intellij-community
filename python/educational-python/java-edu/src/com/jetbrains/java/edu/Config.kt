// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.java.edu

import com.intellij.application.options.ModuleAwareProjectConfigurable
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkModel
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.ui.configuration.JdkComboBox
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.openapi.util.Comparing
import com.intellij.util.ui.JBUI
import com.intellij.webcore.packaging.PackagesNotificationPanel
import com.jetbrains.python.configuration.PyActiveSdkConfigurable
import com.jetbrains.python.packaging.PyPackageManagers
import com.jetbrains.python.packaging.ui.PyInstalledPackagesPanel
import com.jetbrains.python.sdk.PythonSdkType
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.ActionListener
import java.awt.event.ItemEvent
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class ProjectJdkConfigurable(private val myModule: Module) : SearchableConfigurable {

  override fun getId(): String {
    return "com.jetbrains.java.edu.ProjectJdkConfigurable"
  }

  override fun getDisplayName(): String {
    return "Project SDK"
  }

  private val myJdksModel = ProjectSdksModel()
  private var myCbProjectJdk: JdkComboBox? = null
  private var myMainPanel: JPanel? = null

  private val myListener = object : SdkModel.Listener {
    override fun sdkAdded(sdk: Sdk) {
      reloadModel()
    }

    override fun beforeSdkRemove(sdk: Sdk) {
      reloadModel()
    }

    override fun sdkChanged(sdk: Sdk, previousName: String) {
      reloadModel()
    }

    override fun sdkHomeSelected(sdk: Sdk, newSdkHome: String) {
      reloadModel()
    }
  }

  private val selectedProjectJdk: Sdk?
    get() = myJdksModel.findSdk(myCbProjectJdk!!.selectedJdk)

  init {
    myJdksModel.reset(myModule.project)
    myJdksModel.addListener(myListener)
  }

  override fun createComponent(): JComponent? {
    if (myMainPanel == null) {
      myMainPanel = JPanel(GridBagLayout())
      myCbProjectJdk = object : JdkComboBox(myJdksModel, null, null, null, true) {
        override fun getPreferredSize(): Dimension {
          return ui.getPreferredSize(this)
        }
      }
      myCbProjectJdk!!.insertItemAt(JdkComboBox.NoneJdkComboBoxItem(), 0)
      myCbProjectJdk!!.addActionListener(ActionListener {
        myJdksModel.projectSdk = myCbProjectJdk!!.selectedJdk
      })

      val constraints = GridBagConstraints()
      constraints.insets = JBUI.insets(0, 0, 0, 3)
      constraints.fill = GridBagConstraints.HORIZONTAL
      constraints.gridx = 0
      constraints.gridy = 0
      constraints.anchor = GridBagConstraints.LINE_START
      myMainPanel!!.add(JLabel("Project SDK:"), constraints)
      constraints.gridx = 1
      myMainPanel!!.add(myCbProjectJdk!!, constraints)
      val setUpButton = JButton(ApplicationBundle.message("button.new"))
      myCbProjectJdk!!.setSetupButton(setUpButton, myModule.project, myJdksModel, JdkComboBox.NoneJdkComboBoxItem(), null, false)
      constraints.gridx = 2
      constraints.fill = GridBagConstraints.NONE
      myMainPanel!!.add(setUpButton, constraints)

      val notificationsArea = PackagesNotificationPanel()
      val notificationsComponent = notificationsArea.component
      notificationsArea.hide()
      val packagesPanel = PyInstalledPackagesPanel(myModule.project, notificationsArea)
      myCbProjectJdk!!.addItemListener({ e ->
                                         if (e.stateChange == ItemEvent.SELECTED) {
                                           val selectedSdk = myCbProjectJdk!!.selectedJdk
                                           if (selectedSdk != null && selectedSdk.sdkType == PythonSdkType.getInstance()) {
                                             val packageManagers = PyPackageManagers.getInstance()
                                             packagesPanel.updatePackages(packageManagers.getManagementService(myModule.project, selectedSdk))
                                             packagesPanel.updateNotifications(selectedSdk)
                                           }
                                           else {
                                             packagesPanel.updatePackages(null)
                                           }
                                         }
                                       })
      constraints.gridx = 0
      constraints.gridy++
      constraints.weighty = 1.0
      constraints.gridwidth = 3
      constraints.gridheight = GridBagConstraints.RELATIVE
      constraints.fill = GridBagConstraints.BOTH
      myMainPanel!!.add(packagesPanel, constraints)

      constraints.gridheight = GridBagConstraints.REMAINDER
      constraints.gridx = 0
      constraints.gridy++
      constraints.gridwidth = 3
      constraints.weighty = 0.0
      constraints.fill = GridBagConstraints.HORIZONTAL
      constraints.anchor = GridBagConstraints.SOUTH

      myMainPanel!!.add(notificationsComponent, constraints)
    }
    return myMainPanel
  }

  private fun reloadModel() {
    val projectJdk = myJdksModel.projectSdk
    val sdkName = projectJdk?.name ?: ProjectRootManager.getInstance(myModule.project).projectSdkName
    if (sdkName != null) {
      val jdk = myJdksModel.findSdk(sdkName)
      if (jdk != null) {
        myCbProjectJdk!!.selectedJdk = jdk
      }
      else {
        myCbProjectJdk!!.setInvalidJdk(sdkName)
      }
    }
    else {
      myCbProjectJdk!!.selectedJdk = null
    }
  }

  override fun isModified(): Boolean {
    val projectJdk = ModuleRootManager.getInstance(myModule).sdk
    return !Comparing.equal(projectJdk, selectedProjectJdk)
  }

  override fun apply() {
    myJdksModel.apply()
    ApplicationManager.getApplication().runWriteAction({ProjectRootManager.getInstance(myModule.project).projectSdk = selectedProjectJdk})
    ModuleRootModificationUtil.setModuleSdk(myModule, selectedProjectJdk)
  }

  override fun reset() {
    reloadModel()
  }

  override fun disposeUIResources() {
    myJdksModel.removeListener(myListener)
    myMainPanel = null
    myCbProjectJdk = null
  }

}

open class ProjectJdkModuleConfigurable(private val myProject: Project) :
  ModuleAwareProjectConfigurable<UnnamedConfigurable>(myProject, "Project SDK", null) {

  override fun createModuleConfigurable(module: Module): UnnamedConfigurable {
    return ProjectJdkConfigurable(module)
  }

  override fun createDefaultProjectConfigurable(): UnnamedConfigurable? {
    return PyActiveSdkConfigurable(myProject)
  }
}
