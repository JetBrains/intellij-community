// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run

import com.intellij.execution.target.RunConfigurationTargetEnvironmentAdjuster
import com.intellij.openapi.options.SettingsEditor
import com.jetbrains.python.sdk.PythonSdkUtil
import java.util.*

class PyRunConfigurationTargetOptions : PyRunConfigurationEditorExtension {
  private val factoriesCache = WeakHashMap<RunConfigurationTargetEnvironmentAdjuster, PyRunConfigurationEditorFactory>()

  override fun accepts(configuration: AbstractPythonRunConfiguration<out AbstractPythonRunConfiguration<*>>): PyRunConfigurationEditorFactory? {
    val sdk = PythonSdkUtil.findSdkByPath(configuration.sdkHome) ?: return null
    val adjuster = RunConfigurationTargetEnvironmentAdjuster.findTargetEnvironmentRequestAdjuster(sdk) ?: return null
    return if (adjuster.providesAdditionalRunConfigurationUI()) {
      factoriesCache.computeIfAbsent(adjuster) { RunConfigurationsTargetOptionsFactory(adjuster) }
    }
    else {
      null
    }
  }

  private class RunConfigurationsTargetOptionsFactory(private val adjuster: RunConfigurationTargetEnvironmentAdjuster) : PyRunConfigurationEditorFactory {
    override fun createEditor(configuration: AbstractPythonRunConfiguration<*>): SettingsEditor<AbstractPythonRunConfiguration<*>> {
      val sdk = PythonSdkUtil.findSdkByPath(configuration.sdkHome)!!
      val adjuster = RunConfigurationTargetEnvironmentAdjuster.findTargetEnvironmentRequestAdjuster(sdk)!!
      val runConfigurationEditor = adjuster.createAdditionalRunConfigurationUI(configuration) { configuration.sdk }
      return runConfigurationEditor as SettingsEditor<AbstractPythonRunConfiguration<*>>
    }

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as RunConfigurationsTargetOptionsFactory

      if (adjuster != other.adjuster) return false

      return true
    }

    override fun hashCode(): Int {
      return adjuster.hashCode()
    }
  }
}