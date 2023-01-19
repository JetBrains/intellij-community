// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("PyTargetPositionConverters")

package com.jetbrains.python.debugger

import com.intellij.execution.target.TargetEnvironment
import com.intellij.execution.target.getLocalPaths
import com.intellij.execution.target.getTargetPaths
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.AbstractPathMapper
import com.intellij.util.PathMappingSettings
import com.jetbrains.python.debugger.remote.vfs.PyRemotePositionConverter
import com.jetbrains.python.remote.PyRemotePathMapper

/**
 * Creates [PyPositionConverter] for [debugProcess]. The converter uses [pathMappingSettings] for paths resolution and upload volumes
 * declared in [targetEnvironment] if there is no appropriate path mapping found. The converter falls back to the original path if no match
 * is found.
 *
 * The provided [pathMappingSettings] ordinarily contain path mappings stored in Python SDK and the additional path mappings user specified
 * in the run configuration, which the process was created on. The path mappings from Python SDK includes mappings for Python libraries
 * downloaded from the target to the local machine.
 *
 * @param debugProcess the process which IDE requires path mappings for
 * @param targetEnvironment the target environment where the process is instantiated on
 * @param pathMappingSettings the path mappings, which ordinarily come from corresponding Python SDK and corresponding run configuration
 *
 * @see AbstractPathMapper
 * @see getTargetPaths
 */
internal fun createTargetedPositionConverter(debugProcess: PyDebugProcess,
                                             targetEnvironment: TargetEnvironment,
                                             pathMappingSettings: PathMappingSettings): PyPositionConverter {
  val pathMapper = PyTargetPathMapper(targetEnvironment, pathMappingSettings)
  return PyRemotePositionConverter(debugProcess, pathMapper)
}

private class PyTargetPathMapper(private val targetEnvironment: TargetEnvironment,
                                 private val pathMappingSettings: PathMappingSettings) : PyRemotePathMapper() {
  override fun convertToLocal(remotePath: String): String {
    return AbstractPathMapper.convertToLocal(remotePath, pathMappingSettings.pathMappings)
           ?: targetEnvironment.getLocalPaths(remotePath).firstOrNull()
           ?: super.convertToLocal(remotePath)
  }

  override fun convertToRemote(localPath: String): String {
    return AbstractPathMapper.convertToRemote(localPath, pathMappingSettings.pathMappings)
           ?: targetEnvironment.getTargetPaths(FileUtil.toSystemDependentName(localPath)).firstOrNull()
           ?: super.convertToRemote(localPath)
  }

  override fun isEmpty(): Boolean {
    return false
  }
}