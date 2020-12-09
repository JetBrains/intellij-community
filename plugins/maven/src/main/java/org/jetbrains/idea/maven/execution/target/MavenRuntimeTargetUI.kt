// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution.target

import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.execution.target.getRuntimeType
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.layout.*

class MavenRuntimeTargetUI(private val config: MavenRuntimeTargetConfiguration, private val target: TargetEnvironmentConfiguration) :
  BoundConfigurable(config.displayName, config.getRuntimeType().helpTopic) {

  override fun createPanel(): DialogPanel {
    return panel {
      row("Maven home path:") {
        textField(config::homePath)
          .comment("The path to the Maven on the target")
      }
      row("Maven version:") {
        textField(config::versionString)
      }
    }
  }
}