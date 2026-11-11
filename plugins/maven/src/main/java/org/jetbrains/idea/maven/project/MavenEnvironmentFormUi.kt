// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.openapi.ui.ComponentWithBrowseButton
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.TextFieldWithHistory
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.panel
import javax.swing.JCheckBox
import javax.swing.JLabel

internal class MavenEnvironmentFormUi {

  @JvmField
  val mavenHomeLabel = JLabel(MavenConfigurableBundle.message("maven.settings.environment.home.directory"))

  @JvmField
  val mavenHomeField = TextFieldWithHistory()

  lateinit var mavenHomeComponent: ComponentWithBrowseButton<TextFieldWithHistory>
  lateinit var mavenHomeOnTargetHelpLabel: Cell<JLabel>
  lateinit var mavenVersionLabel: JLabel
  lateinit var settingsFileField: TextFieldWithBrowseButton
  lateinit var settingsOverrideCheckBox: JCheckBox
  lateinit var localRepositoryField: TextFieldWithBrowseButton
  lateinit var localRepositoryOverrideCheckBox: JCheckBox

  @JvmField
  val panel = panel {
    row(mavenHomeLabel) {
      mavenHomeComponent = cell(ComponentWithBrowseButton(mavenHomeField, null))
        .align(AlignX.FILL)
        .resizableColumn()
        .component
      mavenHomeOnTargetHelpLabel =
        contextHelp(MavenConfigurableBundle.message("maven.settings.on.targets.environment.home.directory.context.help"))
          .visible(false)
    }

    row("") {
      mavenVersionLabel = label("")
        .component
    }

    row(MavenConfigurableBundle.message("maven.settings.environment.user.settings")) {
      settingsFileField = cell(TextFieldWithBrowseButton())
        .align(AlignX.FILL)
        .resizableColumn()
        .component
      settingsOverrideCheckBox = checkBox(MavenConfigurableBundle.message("maven.settings.override"))
        .component
    }

    row(MavenConfigurableBundle.message("maven.settings.environment.local.repository")) {
      localRepositoryField = cell(TextFieldWithBrowseButton())
        .align(AlignX.FILL)
        .resizableColumn()
        .component
      localRepositoryOverrideCheckBox = checkBox(MavenConfigurableBundle.message("maven.settings.override"))
        .component
    }
  }
}
