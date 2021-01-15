// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.plugins.pipelines.services

import circlet.pipelines.config.dsl.scriptdefinition.ProjectScriptCompilationConfiguration
import circlet.pipelines.config.dsl.scriptdefinition.resolveScriptArtifact
import circlet.pipelines.config.ivy.resolver.ivyResolver
import com.intellij.space.messages.SpaceBundle
import libraries.klogging.logger
import kotlin.script.experimental.api.*
import kotlin.script.experimental.jvm.updateClasspath

internal class SpaceKtsCompilationConfiguration : ScriptCompilationConfiguration(listOf(ProjectScriptCompilationConfiguration()), {
  refineConfiguration {
    beforeCompiling { ha ->
      val config = getAutomationConfigurationOrNull()
      if (config == null) {
        return@beforeCompiling scriptAuthError
      }

      try {
        config.ivyResolver
          .resolveScriptArtifact("com.jetbrains", "space-automation-runtime", "latest.integration")
          .onSuccess { jars ->
            ScriptCompilationConfiguration(ha.compilationConfiguration) { updateClasspath(jars) }.asSuccess()
          }
          .onFailure {
            LOG.warn("Failure while downloading dependencies for .space.kts file $it")
            scriptDependenciesLoadingError
          }
      }
      catch (th: Throwable) {
        LOG.warn(th, "Couldn't download dependencies for .space.kts file")
        scriptDependenciesLoadingError
      }
    }
  }

  ide {
    acceptedLocations(ScriptAcceptedLocation.Everywhere)
  }
}) {
  companion object {
    private val LOG = logger<SpaceKtsCompilationConfiguration>()

    private val scriptAuthError = ResultWithDiagnostics.Failure(
      ScriptDiagnostic(4, SpaceBundle.message("kts.file.login.error"))
    )

    private val scriptDependenciesLoadingError = ResultWithDiagnostics.Failure(
      ScriptDiagnostic(4, SpaceBundle.message("kts.file.dependencies.error"))
    )
  }
}