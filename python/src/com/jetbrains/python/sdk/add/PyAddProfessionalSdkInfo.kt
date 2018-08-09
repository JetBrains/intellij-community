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
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBUI
import java.awt.Component
import javax.swing.Icon
import javax.swing.JPanel
import javax.swing.JTextArea

abstract class PyAddSdkInfoProvider : PyAddSdkProvider {
  override fun createView(project: Project?,
                          module: Module?,
                          newProjectPath: String?,
                          existingSdks: List<Sdk>): PyAddSdkView? =
    if (Registry.`is`("python.show.professional.interpreter.types")) {
      val panel = createPanel()

      panel.panelName = panel.panelName
      panel
    }
    else null

  protected abstract fun createPanel(): PyAddProfessionalSdkInfoPanel
}

class PyAddDockerSdkInfoProvider : PyAddSdkInfoProvider() {
  override fun createPanel() = PyAddProfessionalSdkInfoPanel("Docker", ProfessionalIcons.Docker, "using-docker-as-a-remote-interpreter.html", "Are you developing your code using a Docker image? With PyCharm Professional Edition you can use a Docker container with the same ease of use as a virtualenv: run and debug code with a click of a button.")
}


class PyAddDockerComposeSdkInfoProvider : PyAddSdkInfoProvider() {
  override fun createPanel() = PyAddProfessionalSdkInfoPanel("Docker Compose", ProfessionalIcons.DockerCompose, "using-docker-compose-as-a-remote-interpreter.html", "With Docker Compose you get a reproducible environment not only for your code, but also for backing services like a database. PyCharm Professional integrates seamlessly to give you the run and debug experience you’re used to from local development with containers.")
}

class PyAddSshSdkInfoProvider : PyAddSdkInfoProvider() {
  override fun createPanel() = PyAddProfessionalSdkInfoPanel("SSH Interpreter", ProfessionalIcons.SSH, "configuring-remote-interpreters-via-ssh.html", "Would you like to run and debug code on a remote machine? If you have SSH access to a machine, PyCharm Professional Edition is able to synchronize your code, and debug it remotely with the same ease-of-use you get locally.")
}

class PyAddVagrantSdkInfoProvider : PyAddSdkInfoProvider() {
  override fun createPanel() = PyAddProfessionalSdkInfoPanel("Vagrant", ProfessionalIcons.Vagrant, "configuring-remote-interpreters-via-virtual-boxes.html", "Vagrant allows you to provision VMs for a consistent and reproducible development environment. PyCharm Professional Edition can shorten your development loop by connecting to the Python interpreter within your VM. Run and Debug your code straight from PyCharm.")
}

class PyAddProfessionalSdkInfoPanel(override var panelName: String, override val icon: Icon, val helpPage: String, val text: String) : JPanel(), PyAddSdkView {
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

    val textArea = JTextArea(this.text, 3,80)
    textArea.isEditable = false
    textArea.lineWrap = true
    textArea.isOpaque = false
    textArea.wrapStyleWord = true
    add(VerticalLayout.TOP, textArea)

    add(VerticalLayout.TOP, HyperlinkLabel("Try PyCharm Professional Edition").apply {  addHyperlinkListener {
      val info = ApplicationInfo.getInstance()
      val productVersion = info.majorVersion + "." + info.minorVersionMainPart
      BrowserUtil.browse("https://www.jetbrains.com/pycharm/features/editions_comparison_matrix.html?utm_source=product&utm_medium=link&utm_campaign=PC_INTERPRETER&utm_content=$productVersion")
    }})


    add(VerticalLayout.TOP, HyperlinkLabel("Read more about ${this.panelName} support in PyCharm help").apply {
      addHyperlinkListener {
        BrowserUtil.browse("https://www.jetbrains.com/help/pycharm/$helpPage")
      }
    })

    add(VerticalLayout.TOP, HyperlinkLabel("Got it! Don't show features of Professional Edition here").apply {
      addHyperlinkListener {
        Registry.get("python.show.professional.interpreter.types").setValue(false)
        val list = (this@PyAddProfessionalSdkInfoPanel.parent.parent as Splitter).firstComponent as JBList<*>
        var newList = Lists.newArrayList<PyAddSdkView>()
        for (i in 0 until list.itemsCount) {
          val elementAt = list.model.getElementAt(i)
          if (!(elementAt is PyAddProfessionalSdkInfoPanel)) {
            newList.add(elementAt as PyAddSdkView)
          }
        }
        list.model = JBList.createDefaultListModel(newList)

        list.selectedIndex = 0
      }
    })
  }
}
