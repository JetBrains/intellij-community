// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("PathMappingsUtil")

package com.jetbrains.python.run

import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.target.ExternallySynchronized
import com.intellij.execution.target.TargetEnvironment
import com.intellij.remote.ProcessControlWithMappings
import com.intellij.util.PathMapper
import com.intellij.util.PathMappingSettings
import com.jetbrains.python.run.target.targetEnvironment

fun ProcessHandler.guessPathMapper(): PathMapper? =
  (this as? ProcessControlWithMappings)?.mappingSettings ?: targetEnvironment?.collectAllPathMappings()

fun TargetEnvironment.collectAllPathMappings(): PathMappingSettings {
  val pathMappings = mutableListOf<PathMappingSettings.PathMapping>()
  if (this is ExternallySynchronized) {
    pathMappings += synchronizedVolumes.map(TargetEnvironment.SynchronizedVolume::toPathMapping)
  }
  pathMappings += uploadVolumes.values.map(TargetEnvironment.Volume::toPathMapping)
  pathMappings += downloadVolumes.values.map(TargetEnvironment.Volume::toPathMapping)
  return PathMappingSettings(pathMappings)
}

private fun TargetEnvironment.Volume.toPathMapping(): PathMappingSettings.PathMapping =
  PathMappingSettings.PathMapping(localRoot.toString(), targetRoot)

private fun TargetEnvironment.SynchronizedVolume.toPathMapping(): PathMappingSettings.PathMapping =
  PathMappingSettings.PathMapping(localRootPath.toString(), targetPath)