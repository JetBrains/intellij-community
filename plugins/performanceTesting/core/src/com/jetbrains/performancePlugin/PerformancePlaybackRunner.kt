package com.jetbrains.performancePlugin

import com.intellij.openapi.project.Project

interface PerformancePlaybackRunner {
  fun runScript(project: Project, script: String)
}