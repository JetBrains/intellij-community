package com.intellij.space.vcs

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.space.vcs.CircletProjectContext

open class PostStartupActivity : StartupActivity {
  override fun runActivity(project: Project) {
    CircletProjectContext.getInstance(project) // init service
  }
}
