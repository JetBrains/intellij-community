// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.black.configuration

import com.intellij.openapi.options.ConfigurableProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry

class BlackFormatterConfigurableProvider(val project: Project) : ConfigurableProvider() {

  override fun canCreateConfigurable(): Boolean {
    return Registry.`is`("black.formatter.support.enabled")
  }

  override fun createConfigurable(): BlackFormatterConfigurable {
    return BlackFormatterConfigurable(project)
  }
}