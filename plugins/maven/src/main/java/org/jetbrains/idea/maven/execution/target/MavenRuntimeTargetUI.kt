// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution.target

import com.intellij.execution.target.*
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.toMutableProperty
import org.jetbrains.idea.maven.execution.RunnerBundle.message
import java.util.function.Supplier

class MavenRuntimeTargetUI<C : TargetEnvironmentConfiguration>(private val config: MavenRuntimeTargetConfiguration,
                                                               private val targetType: TargetEnvironmentType<C>,
                                                               private val targetSupplier: Supplier<TargetEnvironmentConfiguration>,
                                                               private val project: Project) :
  BoundConfigurable(config.displayName, config.getRuntimeType().helpTopic) {

  override fun createPanel(): DialogPanel {
    return panel {
      row(message("maven.target.configurable.home.path.label")) {
        if (targetType is BrowsableTargetEnvironmentType) {
          textFieldWithBrowseTargetButton(targetType, targetSupplier,
                                          project,
                                          message("maven.target.configurable.home.path.title"),
                                          config::homePath.toMutableProperty())
            .align(AlignX.FILL)
            .comment(message("maven.target.configurable.home.path.comment"))
        }
        else {
          textField()
            .bindText(config::homePath)
            .align(AlignX.FILL)
            .comment(message("maven.target.configurable.home.path.comment"))
        }
      }
      row(message("maven.target.configurable.version.label")) {
        textField()
          .bindText(config::versionString)
          .align(AlignX.FILL)
      }
    }
  }
}