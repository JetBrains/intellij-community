// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.icons.AllIcons
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.COLUMNS_SHORT
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import org.jetbrains.idea.maven.execution.MavenExecutionOptions
import javax.swing.JCheckBox
import javax.swing.JLabel

internal class MavenGeneralUi(mavenPathsForm: MavenEnvironmentForm) {

  lateinit var checkboxWorkOffline: JCheckBox
  lateinit var checkboxRecursive: JCheckBox
  lateinit var checkboxProduceExceptionErrorMessages: JCheckBox
  lateinit var alwaysUpdateSnapshotsCheckBox: JCheckBox
  lateinit var showDialogWithAdvancedSettingsCheckBox: JCheckBox
  lateinit var outputLevelCombo: ComboBox<MavenExecutionOptions.LoggingLevel>
  lateinit var checksumPolicyCombo: ComboBox<MavenExecutionOptions.ChecksumPolicy>
  lateinit var failPolicyCombo: ComboBox<MavenExecutionOptions.FailureMode>
  lateinit var threadsEditor: JBTextField
  lateinit var useMavenConfigCheckBox: JCheckBox
  private lateinit var mavenConfigWarning: Cell<JLabel>

  @Suppress("UseHtmlChunkToolTip")
  @JvmField
  val panel = panel {
    row {
      checkboxWorkOffline = checkBox(MavenConfigurableBundle.message("maven.settings.general.work.offline"))
        .applyToComponent {
          setToolTipText(MavenConfigurableBundle.message("maven.settings.general.work.offline.tooltip"))
        }
        .component
    }

    row {
      checkboxRecursive = checkBox(MavenConfigurableBundle.message("maven.settings.general.execute.recursively"))
        .applyToComponent {
          setToolTipText(MavenConfigurableBundle.message("maven.settings.general.execute.recursively.tooltip"))
        }
        .component
    }

    row {
      checkboxProduceExceptionErrorMessages = checkBox(MavenConfigurableBundle.message("maven.settings.general.print.stacktraces"))
        .applyToComponent {
          setToolTipText(MavenConfigurableBundle.message("maven.settings.general.print.stacktraces.tooltip"))
        }
        .component
    }

    row {
      alwaysUpdateSnapshotsCheckBox = checkBox(MavenConfigurableBundle.message("maven.settings.general.update.snapshots"))
        .applyToComponent {
          setToolTipText(MavenConfigurableBundle.message("maven.settings.general.update.snapshots.tooltip"))
        }
        .component
    }

    row {
      showDialogWithAdvancedSettingsCheckBox =
        checkBox(MavenConfigurableBundle.message("maven.settings.environment.show.advanced.settings"))
          .applyToComponent {
            setToolTipText(MavenConfigurableBundle.message("maven.settings.environment.show.advanced.settings.tooltip"))
          }
          .component
    }

    row(MavenConfigurableBundle.message("maven.settings.general.output.level")) {
      outputLevelCombo = comboBox(MavenExecutionOptions.LoggingLevel.entries, textListCellRenderer("") { it.displayString })
        .component
    }

    row(MavenConfigurableBundle.message("maven.settings.general.checksum.policy")) {
      checksumPolicyCombo = comboBox(MavenExecutionOptions.ChecksumPolicy.entries, textListCellRenderer("") { it.displayString })
        .component
    }

    row(MavenConfigurableBundle.message("maven.settings.general.multiproject.build.policy")) {
      failPolicyCombo = comboBox(MavenExecutionOptions.FailureMode.entries, textListCellRenderer("") { it.displayString })
        .component
    }

    row(MavenConfigurableBundle.message("maven.settings.general.thread.count")) {
      threadsEditor = textField()
        .columns(COLUMNS_SHORT)
        .commentRight(MavenConfigurableBundle.message("maven.settings.general.thread.count.note"))
        .applyToComponent {
          setToolTipText(MavenConfigurableBundle.message("maven.settings.general.thread.count.tooltip"))
        }.component
    }

    row {
      cell(mavenPathsForm.createComponent())
        .align(AlignX.FILL)
    }

    row {
      useMavenConfigCheckBox = checkBox(MavenConfigurableBundle.message("maven.settings.environment.use.maven.config.settings"))
        .applyToComponent {
          setToolTipText(MavenConfigurableBundle.message("maven.settings.environment.use.maven.config.settings.tooltip"))
        }
        .component
    }

    row {
      mavenConfigWarning = label(MavenConfigurableBundle.message("maven.settings.environment.use.maven.config.settings.hint"))
        .applyToComponent {
          icon = AllIcons.General.BalloonWarning
        }.visible(false)
    }
  }

  fun setMavenConfigWarningVisible(isVisible: Boolean) {
    mavenConfigWarning.visible(isVisible)
  }
}
