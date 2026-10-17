// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.openapi.project.Project
import com.intellij.remote.RemoteMappingsManager
import com.intellij.remote.RemoteSdkProperties
import com.intellij.remote.TargetAwarePathMappingProvider
import com.intellij.util.PathMappingSettings
import com.jetbrains.python.remote.PyRemotePathMapper
import com.jetbrains.python.remote.PythonRemoteInterpreterManager
import com.jetbrains.python.target.PyTargetAwareAdditionalData

/**
 * Creates the [PyRemotePathMapper] of a target SDK with [data]: the helpers, the SDK and the project mappings, and the
 * user-defined [mappingSettings].
 */
internal fun createTargetPathMapperImpl(
  project: Project,
  mappingSettings: PathMappingSettings,
  data: PyTargetAwareAdditionalData,
): PyRemotePathMapper {
  val remotePathMapper = appendBasicMappings(project, data)
  remotePathMapper.addAll(mappingSettings.pathMappings, PyRemotePathMapper.PyPathMappingType.USER_DEFINED)
  return remotePathMapper
}

private fun appendBasicMappings(project: Project, data: RemoteSdkProperties): PyRemotePathMapper {
  val pathMapper = PyRemotePathMapper()
  PythonRemoteInterpreterManager.addHelpersMapping(data, pathMapper)

  // We don't want to resolve remote paths to sources copied from the remote when they're available locally
  val deploymentPaths = getDeploymentPaths(project, data)
  pathMapper.addAll(
    data.pathMappings.pathMappings.filter { it.remoteRoot !in deploymentPaths },
    PyRemotePathMapper.PyPathMappingType.SYS_PATH
  )

  val mappings = RemoteMappingsManager.getInstance(project).getForServer(PythonRemoteInterpreterManager.PYTHON_PREFIX, data.sdkId)
  if (mappings != null) {
    pathMapper.addAll(mappings.settings, PyRemotePathMapper.PyPathMappingType.USER_DEFINED)
  }
  return pathMapper
}

/**
 * Collects deployment paths from suitable mapping providers.
 *
 * If [data] is not a [PyTargetAwareAdditionalData], then an empty set is returned.
 *
 * @param project the project for which deployment paths are being retrieved
 * @param data SDK additional data
 * @return a set of paths on remote file systems
 */
private fun getDeploymentPaths(project: Project, data: RemoteSdkProperties): Set<String> {
  val deploymentPaths = mutableSetOf<String>()
  if (data is PyTargetAwareAdditionalData) {
    for (provider in TargetAwarePathMappingProvider.getSuitableMappingProviders(data)) {
      for (pathMapping in provider.getPathMappingSettings(project, data).pathMappings) {
        deploymentPaths.add(pathMapping.remoteRoot)
      }
    }
  }
  return deploymentPaths
}
