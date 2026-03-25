// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution

import com.intellij.execution.configuration.EnvironmentVariablesComponent
import com.intellij.openapi.externalSystem.service.ui.ExternalSystemJdkComboBox
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.ClientProperty
import com.intellij.ui.RawCommandLineEditor
import com.intellij.ui.UserActivityWatcher
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.idea.maven.project.MavenConfigurableBundle
import java.awt.BorderLayout
import javax.swing.JCheckBox

internal class MavenRunnerUi(project: Project, runConfigurationMode: Boolean, properties: Map<String?, String?>) {

  @JvmField
  val delegateToMavenCheckbox = JCheckBox(MavenConfigurableBundle.message("maven.settings.runner.delegate"))

  @JvmField
  val vmParametersEditor = RawCommandLineEditor()

  @JvmField
  val envVariablesComponent = EnvironmentVariablesComponent()

  @JvmField
  val jdkCombo = ExternalSystemJdkComboBox(project)
  lateinit var targetJdkCombo: ComboBox<String>
  lateinit var skipTestsCheckBox: JCheckBox

  @JvmField
  val propertiesPanel = MavenPropertiesPanel(properties)

  private lateinit var jdkRow: Row
  private lateinit var targetJdkRow: Row

  @JvmField
  val panel = panel {
    if (!runConfigurationMode) {
      row {
        cell(delegateToMavenCheckbox)
      }
    }

    row(MavenConfigurableBundle.message("maven.settings.runner.vm.options")) {
      cell(vmParametersEditor)
        .align(AlignX.FILL)
        .comment(MavenConfigurableBundle.message("maven.settings.vm.options.tooltip"))
    }
    jdkRow = row(MavenConfigurableBundle.message("maven.settings.runner.jre")) {
      cell(jdkCombo)
        .align(AlignX.FILL)
    }
    targetJdkRow = row(MavenConfigurableBundle.message("maven.settings.runner.jre")) {
      targetJdkCombo = comboBox(emptyList<String>())
        .align(AlignX.FILL)
        .applyToComponent {
          ClientProperty.put(this, UserActivityWatcher.DO_NOT_WATCH, true)
        }
        .component
    }

    row {
      cell(envVariablesComponent)
        .align(AlignX.FILL)
        .applyToComponent {
          isPassParentEnvs = true
          labelLocation = BorderLayout.WEST
        }
    }

    group(MavenConfigurableBundle.message("maven.settings.runner.properties"), indent = false) {
      row {
        skipTestsCheckBox = checkBox(MavenConfigurableBundle.message("maven.settings.runner.skip.tests"))
          .component
      }

      row {
        cell(propertiesPanel)
          .align(Align.FILL)
          .applyToComponent {
            table.setShowGrid(false)
            emptyText.text = MavenConfigurableBundle.message("maven.settings.runner.properties.not.defined")
          }
      }.resizableRow()
    }.resizableRow()
  }

  init {
    setState(true)
  }

  fun setState(localTarget: Boolean) {
    jdkRow.visible(localTarget)
    targetJdkRow.visible(!localTarget)
  }
}
