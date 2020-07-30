package com.intellij.space.plugins.pipelines.services

import com.intellij.build.AbstractViewManager
import com.intellij.openapi.project.Project

class SyncDslViewManager(project: Project) : AbstractViewManager(project) {
  public override fun getViewName(): String {
    return "Space Automation DSL"
  }
}
