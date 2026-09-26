package com.intellij.python.typeEngine.frontend

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry

/**
 * Provider for Type Engine configurable.
 */
class PyChooserTypeEngineConfigurableProvider(private val project: Project) : ConfigurableProvider() {
  override fun canCreateConfigurable(): Boolean {
    return Registry.`is`("pycharm.type.engine", true)
  }

  override fun createConfigurable(): Configurable = PyTypeEngineConfigurable(project)
}
