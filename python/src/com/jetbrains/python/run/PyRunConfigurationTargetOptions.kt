// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run

import com.intellij.execution.target.RunConfigurationTargetEnvironmentAdjuster
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.projectRoots.Sdk
import org.jetbrains.annotations.TestOnly
import java.util.*

class PyRunConfigurationTargetOptions : PyRunConfigurationEditorExtension {
  private val factoriesCache = WeakHashMap<RunConfigurationTargetEnvironmentAdjuster, PyRunConfigurationEditorFactory>()

  override fun accepts(configuration: AbstractPythonRunConfiguration<out AbstractPythonRunConfiguration<*>>): PyRunConfigurationEditorFactory? {
    val sdk = configuration.sdk ?: return null
    return acceptsForSdk(sdk)
  }

  private fun acceptsForSdk(sdk: Sdk): PyRunConfigurationEditorFactory? {
    val adjuster = RunConfigurationTargetEnvironmentAdjuster.Factory.findTargetEnvironmentRequestAdjuster(sdk) ?: return null
    return if (adjuster.providesAdditionalRunConfigurationUI()) {
      factoriesCache.computeIfAbsent(adjuster) { RunConfigurationsTargetOptionsFactory(adjuster) }
    }
    else {
      null
    }
  }

  @TestOnly
  fun accepts(sdk: Sdk): PyRunConfigurationEditorFactory? = acceptsForSdk(sdk)

  private class RunConfigurationsTargetOptionsFactory(private val adjuster: RunConfigurationTargetEnvironmentAdjuster) : PyRunConfigurationEditorFactory {
    override fun createEditor(configuration: AbstractPythonRunConfiguration<*>): SettingsEditor<AbstractPythonRunConfiguration<*>> {
      val adjuster = RunConfigurationTargetEnvironmentAdjuster.Factory.findTargetEnvironmentRequestAdjuster(configuration.sdk!!)!!
      val runConfigurationEditor = adjuster.createAdditionalRunConfigurationUI(configuration) { configuration.sdk }
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