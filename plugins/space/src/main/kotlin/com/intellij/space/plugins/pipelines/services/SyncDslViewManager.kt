package com.intellij.space.plugins.pipelines.services

import com.intellij.build.AbstractViewManager
import com.intellij.openapi.project.Project
import com.intellij.space.messages.SpaceBundle

class SyncDslViewManager(project: Project) : AbstractViewManager(project) {
  public override fun getViewName() = SpaceBundle.message("tab.title.space.automation.dsl")
}
