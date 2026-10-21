// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel

class MavenTestRunningConfigurable(private val project: Project) :
  BoundSearchableConfigurable(MavenProjectBundle.message("maven.testRunning"), "reference.settings.project.maven.testRunning") {

  private val settings: MavenTestRunningSettings
    get() = MavenProjectSettings.getInstance(project).testRunningSettings

  override fun createPanel(): DialogPanel {
    return panel {
      buttonsGroup(MavenConfigurableBundle.message("maven.settings.testrun.caption")) {
        row {
          checkBox(MavenConfigurableBundle.message("maven.settings.testrun.argline"))
            .bindSelected({ settings.isPassArgLine }, { settings.isPassArgLine = it })
        }
        row {
          checkBox(MavenConfigurableBundle.message("maven.settings.testrun.system.property"))
            .bindSelected({ settings.isPassSystemProperties }, { settings.isPassSystemProperties = it })
        }
        row {
          checkBox(MavenConfigurableBundle.message("maven.settings.testrun.env.variables"))
            .bindSelected({ settings.isPassEnvironmentVariables }, { settings.isPassEnvironmentVariables = it })
        }
      }
    }
  }
}
