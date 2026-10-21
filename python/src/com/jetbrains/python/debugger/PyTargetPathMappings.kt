// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger

import com.intellij.openapi.project.Project
import com.intellij.remote.RemoteMappingsManager
import com.intellij.remote.TargetAwarePathMappingProvider
import com.intellij.util.PathMappingSettings
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.remote.PythonRemoteInterpreterManager
import com.jetbrains.python.target.PyTargetAwareAdditionalData
import org.jetbrains.annotations.ApiStatus

/**
 * The path mappings of a process on the target of this SDK, as far as the IDE knows them before the process starts.
 *
 * The order is the one [PyTargetPathMapper] applies: [runConfigurationMappings], the deployment mappings of the target,
 * the mappings of this SDK, and the mappings the user set for the server. An SDK mapping to a deployment path is dropped,
 * because it points at a copy of the sources that the IDE downloaded, and the deployment mapping already points at the
 * sources themselves.
 *
 * The mappings of an upload volume are not here. They exist only after the environment is prepared.
 *
 * @param project the project whose deployment and server mappings apply
 * @param runConfigurationMappings the mappings of the run configuration, or `null` when it has none
 */
@ApiStatus.Internal
@RequiresBackgroundThread
fun PyTargetAwareAdditionalData.collectPathMappings(project: Project, runConfigurationMappings: PathMappingSettings?): PathMappingSettings {
  val result = PathMappingSettings()
  runConfigurationMappings?.let(result::addAll)

  val deploymentMappings = TargetAwarePathMappingProvider.getSuitableMappingProviders(this)
    .flatMap { it.getPathMappingSettings(project, this).pathMappings }
  result.addAll(deploymentMappings)

  val deploymentPaths = deploymentMappings.mapTo(HashSet()) { it.remoteRoot }
  result.addAll(pathMappings.pathMappings.filter { it.remoteRoot !in deploymentPaths })

  RemoteMappingsManager.getInstance(project).getForServer(PythonRemoteInterpreterManager.PYTHON_PREFIX, sdkId)
    ?.let { result.addAll(it.settings) }
  return result
}
