// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.miscProject.impl

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.pycharm.community.ide.impl.miscProject.MiscFileType
import com.intellij.pycharm.community.ide.impl.miscProject.PyMiscService
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus

/**
 * Action displayed on welcome screen to create a project by [miscFileType]
 */
@ApiStatus.Internal
open class PyMiscFileAction(private val miscFileType: MiscFileType) : AnAction(
  miscFileType.title,
  null,
  miscFileType.icon
) {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  @RequiresEdt
  override fun actionPerformed(e: AnActionEvent) {
    MiscProjectUsageCollector.projectCreated(miscFileType)
    PyMiscService.getInstance().createMiscProject(
      e.project,
      miscFileType
    )
  }
}
