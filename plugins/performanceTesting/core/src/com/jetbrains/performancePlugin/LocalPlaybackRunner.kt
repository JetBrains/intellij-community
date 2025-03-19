package com.jetbrains.performancePlugin

import com.intellij.openapi.project.Project


class LocalPlaybackRunner: PerformancePlaybackRunner {
  override fun runScript(project: Project, script: String) {
    PerformanceTestSpan.makeTestSpanCurrent()
    PlaybackRunnerExtended(script, CommandLogger(), project).runBlocking(0)
  }
}