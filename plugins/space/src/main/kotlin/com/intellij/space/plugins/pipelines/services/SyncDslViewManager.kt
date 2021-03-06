// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.plugins.pipelines.services

import com.intellij.build.AbstractViewManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.space.messages.SpaceBundle

@Service
class SyncDslViewManager(project: Project) : AbstractViewManager(project) {
  public override fun getViewName() = SpaceBundle.message("tab.title.space.automation.dsl")
}
