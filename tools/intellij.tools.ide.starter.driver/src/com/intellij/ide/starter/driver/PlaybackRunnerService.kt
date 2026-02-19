package com.intellij.ide.starter.driver

import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.Project

@Remote("com.jetbrains.performancePlugin.PlaybackRunnerService", plugin = "com.jetbrains.performancePlugin")
interface PlaybackRunnerService {
  fun runScript(project: Project, script: String)
}
