// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.configuration.interpreter

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableProvider
import com.intellij.openapi.project.Project
import com.intellij.pycharm.community.ide.impl.PyCharmPyActiveSdkModuleConfigurable

/**
 * Registers the legacy PyCharm "Interpreter" settings page via a provider so it can be hidden
 * behind the redesign flag without touching the class itself.
 */
internal class PyCharmPyActiveSdkModuleConfigurableProvider(private val project: Project) : ConfigurableProvider() {
  override fun canCreateConfigurable(): Boolean = !PyInterpreterRedesignFlags.isEnabled()

  override fun createConfigurable(): Configurable = PyCharmPyActiveSdkModuleConfigurable(project)
}
