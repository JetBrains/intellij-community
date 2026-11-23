// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run

import com.intellij.execution.target.RunConfigurationTargetEnvironmentAdjuster
import com.intellij.openapi.options.SettingsEditor
import com.intellij.python.sdk.backend.createRunConfigurationUI
import com.intellij.python.sdk.backend.runConfigurationTargetAdjuster
import org.jetbrains.annotations.ApiStatus
import java.util.WeakHashMap

@ApiStatus.Internal
class PyRunConfigurationTargetOptions : PyRunConfigurationEditorExtension {
  private val factoriesCache = WeakHashMap<RunConfigurationTargetEnvironmentAdjuster, PyRunConfigurationEditorFactory>()

  override fun accepts(configuration: AbstractPythonRunConfiguration<out AbstractPythonRunConfiguration<*>>): PyRunConfigurationEditorFactory? {
    val adjuster = configuration.pythonInterpreter?.runConfigurationTargetAdjuster() ?: return null
    return if (adjuster.providesAdditionalRunConfigurationUI()) {
      factoriesCache.computeIfAbsent(adjuster) { RunConfigurationsTargetOptionsFactory(adjuster) }
    }
    else {
      null
    }
  }

  private class RunConfigurationsTargetOptionsFactory(private val adjuster: RunConfigurationTargetEnvironmentAdjuster) : PyRunConfigurationEditorFactory {
    override fun createEditor(configuration: AbstractPythonRunConfiguration<*>): SettingsEditor<AbstractPythonRunConfiguration<*>> {
      val adjuster = configuration.pythonInterpreter!!.runConfigurationTargetAdjuster()!!
      val runConfigurationEditor = adjuster.createRunConfigurationUI(configuration) { configuration.pythonInterpreter }
      @Suppress("UNCHECKED_CAST") // TODO: Express it in the type-safe manner
      return runConfigurationEditor as SettingsEditor<AbstractPythonRunConfiguration<*>>
    }

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as RunConfigurationsTargetOptionsFactory

      return adjuster == other.adjuster
    }

    override fun hashCode(): Int {
      return adjuster.hashCode()
    }
  }
}