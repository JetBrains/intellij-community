// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution.target

import com.intellij.execution.target.BrowsableTargetEnvironmentConfiguration
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.execution.target.TargetUIUtil
import com.intellij.execution.target.getRuntimeType
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.layout.*
import org.jetbrains.idea.maven.execution.RunnerBundle.message

class MavenRuntimeTargetUI(private val config: MavenRuntimeTargetConfiguration,
                           private val target: TargetEnvironmentConfiguration,
                           private val project: Project) :
  BoundConfigurable(config.displayName, config.getRuntimeType().helpTopic) {

  override fun createPanel(): DialogPanel {
    return panel {
      row(message("maven.target.configurable.home.path.label")) {
        val cellBuilder: CellBuilder<*>
        if (target is BrowsableTargetEnvironmentConfiguration) {
          cellBuilder = TargetUIUtil.textFieldWithBrowseButton(this, target, project,
                                                               message("maven.target.configurable.home.path.title"),
                                                               config::homePath.toBinding())
        }
        else {
          cellBuilder = textField(config::homePath)
        }
        cellBuilder.comment(message("maven.target.configurable.home.path.comment"))
      }
      row(message("maven.target.configurable.version.label")) {
        textField(config::versionString)
      }
    }
  }
}