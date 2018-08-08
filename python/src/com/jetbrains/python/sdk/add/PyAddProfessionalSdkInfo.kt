// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.add

import com.google.common.collect.Lists
import com.google.common.collect.Maps
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.labels.SwingActionLink
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBUI
import java.awt.Component
import java.awt.event.ActionEvent
import javax.swing.*

const val PROFESSIONAL = "(Professional)"

abstract class PyAddSdkInfoProvider : PyAddSdkProvider {
  override fun createView(project: Project?,
                          module: Module?,
                          newProjectPath: String?,
                          existingSdks: List<Sdk>): PyAddSdkView? =
    if (Registry.`is`("python.show.professional.interpreter.types")) {
      val panel = createPanel()

      panel.panelName = panel.panelName + " " + PROFESSIONAL
      panel
    }
    else null

  protected abstract fun createPanel(): PyAddProfessionalSdkInfoPanel
}

class PyAddDockerSdkInfoProvider : PyAddSdkInfoProvider() {
  override fun createPanel() = PyAddProfessionalSdkInfoPanel("Docker", ProfessionalIcons.Docker, "using-docker-as-a-remote-interpreter.html")
}


class PyAddDockerComposeSdkInfoProvider : PyAddSdkInfoProvider() {
  override fun createPanel() = PyAddProfessionalSdkInfoPanel("Docker Compose", ProfessionalIcons.DockerCompose, "using-docker-compose-as-a-remote-interpreter.html")
}

class PyAddSshSdkInfoProvider : PyAddSdkInfoProvider() {
  override fun createPanel() = PyAddProfessionalSdkInfoPanel("SSH Interpreter", ProfessionalIcons.SSH, "configuring-remote-interpreters-via-ssh.html")
}

class PyAddVagrantSdkInfoProvider : PyAddSdkInfoProvider() {
  override fun createPanel() = PyAddProfessionalSdkInfoPanel("Vagrant", ProfessionalIcons.Vagrant, "configuring-remote-interpreters-via-virtual-boxes.html")
}

class PyAddProfessionalSdkInfoPanel(override var panelName: String, override val icon: Icon, val helpPage: String) : JPanel(), PyAddSdkView {
  override fun getOrCreateSdk(): Sdk? = null


  override fun onSelected() {
  }

  override val actions: Map<PyAddSdkDialogFlowAction, Boolean>
    get() = Maps.newHashMap()

  override val component: Component
    get() = this

  override fun previous() {
  }

  override fun next() {
  }

  override fun complete() {

  }

  override fun validateAll(): List<ValidationInfo> = Lists.newArrayList(
    ValidationInfo("This feature is only available in PyCharm Professional Edition"))

  override fun addStateListener(stateListener: PyAddSdkStateListener) {
  }

  init {
    layout = VerticalLayout(JBUI.scale(10))
    val panel = JPanel(HorizontalLayout(JBUI.scale(0)))
    panel.add(HorizontalLayout.LEFT,
              JBLabel("Support for ${this.panelName} is available in "))
    panel.add(HorizontalLayout.LEFT, SwingActionLink(object : AbstractAction("PyCharm Professional Edition") {
      override fun actionPerformed(e: ActionEvent?) {
        val info = ApplicationInfo.getInstance()
        val productVersion = info.majorVersion + "." + info.minorVersionMainPart
        BrowserUtil.browse("https://www.jetbrains.com/pycharm/features/editions_comparison_matrix.html?utm_source=product&utm_medium=link&utm_campaign=PC_INTERPRETER&utm_content=$productVersion")
      }
    }))
    add(VerticalLayout.TOP, panel)

    val panel2 = JPanel(HorizontalLayout(JBUI.scale(0)))
    panel2.add(HorizontalLayout.LEFT,
              JBLabel("Read more about ${this.panelName} in "))
    panel2.add(HorizontalLayout.LEFT, SwingActionLink(object : AbstractAction(" PyCharm help") {
      override fun actionPerformed(e: ActionEvent?) {
        BrowserUtil.browse("https://www.jetbrains.com/help/pycharm/$helpPage")
      }
    }))
    add(VerticalLayout.TOP, panel2)

    add(VerticalLayout.TOP, SwingActionLink(object : AbstractAction("Got it! Don't show features of Professional Edition here") {
      override fun actionPerformed(e: ActionEvent?) {
        Registry.get("python.show.professional.interpreter.types").setValue(false)
        val list = (this@PyAddProfessionalSdkInfoPanel.parent.parent as Splitter).firstComponent as JBList<*>
        var newList = Lists.newArrayList<PyAddSdkView>()
        for (i in 0 until list.itemsCount) {
          val elementAt = list.model.getElementAt(i)
          if (!(elementAt as PyAddSdkView).panelName.endsWith(PROFESSIONAL)) {
            newList.add(elementAt)
          }
        }
        list.model = JBList.createDefaultListModel(newList)

        list.selectedIndex = 0
      }
    }))
  }
}
