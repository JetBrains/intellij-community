// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin

import com.intellij.openapi.project.Project


class LocalPlaybackRunner: PerformancePlaybackRunner {
  override fun runScript(project: Project, script: String) {
    PerformanceTestSpan.makeTestSpanCurrent()
    PlaybackRunnerExtended(script, CommandLogger(), project).runBlocking(0)
  }
}