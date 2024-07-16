package com.jetbrains.performancePlugin

import com.intellij.openapi.components.Service
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

@Service
class PlaybackRunnerService {

  fun runScript(project: Project, script: String){
    EP_NAME.extensionList.first().runScript(project, script)
  }

  val EP_NAME: ExtensionPointName<PerformancePlaybackRunner> = ExtensionPointName("com.jetbrains.performancePlugin.playbackRunnerProvider")
}
