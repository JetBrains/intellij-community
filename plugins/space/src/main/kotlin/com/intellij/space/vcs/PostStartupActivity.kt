// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity

open class PostStartupActivity : StartupActivity {
  override fun runActivity(project: Project) {
    SpaceProjectContext.getInstance(project) // init service
  }
}
