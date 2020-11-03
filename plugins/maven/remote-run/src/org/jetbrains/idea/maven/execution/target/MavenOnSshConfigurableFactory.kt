// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution.target

import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.execution.target.getRuntimeType
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.TextComponentAccessor
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.layout.*
import com.jetbrains.plugins.remotesdk.target.ssh.target.SshTargetEnvironmentConfiguration
import com.jetbrains.plugins.remotesdk.target.ssh.target.SshTargetType
import org.jetbrains.idea.maven.execution.RunnerBundle.message

class MavenOnSshConfigurableFactory : MavenRuntimeUIFactory {
  override fun createConfigurable(project: Project,
                                  mavenRuntimeTargetConfiguration: MavenRuntimeTargetConfiguration,
                                  targetEnvironmentConfiguration: TargetEnvironmentConfiguration): Configurable? {
    if (targetEnvironmentConfiguration !is SshTargetEnvironmentConfiguration) return null
    return MavenOnSshTargetUI(project, mavenRuntimeTargetConfiguration, targetEnvironmentConfiguration)
  }

  class MavenOnSshTargetUI(private val project: Project,
                           private val config: MavenRuntimeTargetConfiguration,
                           private val target: SshTargetEnvironmentConfiguration) : BoundConfigurable(config.displayName,
                                                                                                      config.getRuntimeType().helpTopic) {
    override fun createPanel(): DialogPanel {
      return panel {
        row(message("maven.target.configurable.home.path.label")) {
          val textFieldWithBrowseButton = TextFieldWithBrowseButton()
          val browser = SshTargetType.createFileBrowser(project, { target.findSshConfig(project) },
                                                        message("maven.target.configurable.home.path.label"),
                                                        TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT,
                                                        textFieldWithBrowseButton.textField)
          textFieldWithBrowseButton.addActionListener(browser)
          textFieldWithBrowseButton.text = config.homePath
          component(textFieldWithBrowseButton)
            .withBinding(TextFieldWithBrowseButton::getText, TextFieldWithBrowseButton::setText, config::homePath.toBinding())
            .comment(message("maven.target.configurable.home.path.comment"), forComponent = true)
        }
      }
    }
  }
}

