// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven

import com.intellij.ide.CommandLineInspectionProjectConfigurator
import com.intellij.ide.CommandLineInspectionProjectConfigurator.*
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.idea.maven.project.MavenProjectBundle

private const val MAVEN_CREATE_DUMMY_MODULE_ON_FIRST_IMPORT_REGISTRY_KEY = "maven.create.dummy.module.on.first.import"

class MavenCommandLineInspectionProjectConfigurator : CommandLineInspectionProjectConfigurator {
  override fun getName(): String = "maven"

  override fun getDescription(): String = MavenProjectBundle.message("maven.commandline.description")

  override fun configureEnvironment(context: ConfiguratorContext) = context.run {
    Registry.get(MAVEN_CREATE_DUMMY_MODULE_ON_FIRST_IMPORT_REGISTRY_KEY).setValue(false)
  }
}