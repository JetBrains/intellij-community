// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin

import com.intellij.openapi.project.Project

interface PerformancePlaybackRunner {
  fun runScript(project: Project, script: String)
}