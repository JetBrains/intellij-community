// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.plugins.pipelines.services

import circlet.pipelines.config.dsl.scriptdefinition.ProjectScriptCompilationConfiguration
import circlet.pipelines.config.dsl.scriptdefinition.resolveScriptArtifact
import circlet.pipelines.config.ivy.resolver.ivyResolver
import com.intellij.openapi.diagnostic.logger
import com.intellij.space.messages.SpaceBundle
import kotlin.script.experimental.api.*
import kotlin.script.experimental.jvm.updateClasspath

internal class SpaceKtsCompilationConfiguration : ScriptCompilationConfiguration(ProjectScriptCompilationConfiguration(), body = {
  refineConfiguration {
    beforeCompiling { context ->
      addAutomationRuntimeToClasspath(context)
    }
  }

  ide {
    acceptedLocations(ScriptAcceptedLocation.Everywhere)
  }
}) {
  companion object {
    private val LOG = logger<SpaceKtsCompilationConfiguration>()

    private val AUTH_ERROR = ResultWithDiagnostics.Failure(
      ScriptDiagnostic(4, SpaceBundle.message("kts.file.login.error"))
    )

    private val DEPENDENCIES_LOADING_ERROR = ResultWithDiagnostics.Failure(
      ScriptDiagnostic(4, SpaceBundle.message("kts.file.dependencies.error"))
    )

    private fun addAutomationRuntimeToClasspath(context: ScriptConfigurationRefinementContext): ResultWithDiagnostics<ScriptCompilationConfiguration> {
      val config = getAutomationConfigurationOrNull()
      if (config == null) {
        LOG.debug("[space-kts] User not logged in to Space, cannot fetch Automation runtime")
        return AUTH_ERROR
      }

      try {
        LOG.debug("[space-kts] Resolving space-automation-runtime from ${config.locationDescription}")

        return config.ivyResolver
          .resolveScriptArtifact("com.jetbrains", "space-automation-runtime", "latest.integration")
          .onSuccess { jars ->
            LOG.debug("[space-kts] Successfully resolved space-automation-runtime: ${jars.joinToString()}")
            ScriptCompilationConfiguration(context.compilationConfiguration) { updateClasspath(jars) }.asSuccess()
          }
          .onFailure { diagnostics ->
            LOG.warn("[space-kts] Failure while downloading dependencies for .space.kts file: $diagnostics")
            DEPENDENCIES_LOADING_ERROR
          }
      }
      catch (th: Throwable) {
        LOG.warn("[space-kts] Couldn't download dependencies for .space.kts file", th)
        return DEPENDENCIES_LOADING_ERROR
      }
    }
  }
}
