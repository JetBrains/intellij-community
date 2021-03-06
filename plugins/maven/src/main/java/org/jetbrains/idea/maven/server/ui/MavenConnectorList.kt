// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server.ui

import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.layout.*
import org.jetbrains.idea.maven.project.MavenConfigurableBundle
import java.awt.event.ActionListener
import javax.swing.Action
import javax.swing.JButton
import javax.swing.JComponent

class MavenConnectorList {
  private val myPanel: DialogPanel
  private lateinit var myTable: ConnectorTable

  init {
    myPanel = createPanel()
  }

  private fun createPanel() = panel {
    row {
      label(MavenConfigurableBundle.message("connector.ui.label"))
    }
    row {
      myTable = ConnectorTable()
      myTable.component(grow, push)
    }
  }

  fun show() {
    val dialogWrapper = object : DialogWrapper(true) {
      init {
        title = MavenConfigurableBundle.message("connector.ui.label")
        isModal = false
        `init`()
      }

      override fun createCenterPanel(): JComponent {
        return myPanel
      }

    }
    dialogWrapper.show()
  }
}

