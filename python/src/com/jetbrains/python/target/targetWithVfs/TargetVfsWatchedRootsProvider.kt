// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.target.targetWithVfs

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.WatchedRootsProvider
import com.jetbrains.python.run.PythonInterpreterTargetEnvironmentFactory
import com.jetbrains.python.sdk.targetAdditionalData
import com.jetbrains.python.statistics.sdks

class TargetVfsWatchedRootsProvider : WatchedRootsProvider {
  override fun getRootsToWatch(project: Project): Set<String> {
    val result = mutableSetOf<String>()
    for (data in project.sdks.mapNotNull { it.targetAdditionalData }) {
      val mapper = data.targetEnvironmentConfiguration?.let { PythonInterpreterTargetEnvironmentFactory.getTargetWithMappedLocalVfs(it) }
                   ?: continue
      for (remotePath in data.pathMappings.pathMappings.map { it.remoteRoot })
        // children must be loaded for events to work
        mapper.getVfsFromTargetPath(remotePath)?.let { it.children; result.add(it.path) }
    }
    return result
  }
}