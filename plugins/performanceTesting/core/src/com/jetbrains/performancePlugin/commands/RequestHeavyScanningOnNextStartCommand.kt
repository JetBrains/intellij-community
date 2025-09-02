// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import com.intellij.util.indexing.dependencies.ProjectIndexingDependenciesService
import org.jetbrains.annotations.NonNls

class RequestHeavyScanningOnNextStartCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX: @NonNls String = CMD_PREFIX + "requestHeavyScanningOnNextStart"
  }


  override suspend fun doExecute(context: PlaybackContext) {
    val service = context.project.serviceAsync<ProjectIndexingDependenciesService>()
    service.requestHeavyScanningOnProjectOpen("Playback command $PREFIX")
  }
}