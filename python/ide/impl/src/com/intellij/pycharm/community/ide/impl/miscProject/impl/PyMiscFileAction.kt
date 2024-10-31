// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.miscProject.impl

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.pycharm.community.ide.impl.PyCharmCommunityCustomizationBundle
import com.intellij.pycharm.community.ide.impl.miscProject.MiscFileType
import com.intellij.util.SystemProperties
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jetbrains.python.Result
import kotlinx.coroutines.CoroutineScope
import java.nio.file.Path

/**
 * Action displayed on welcome screen to create a project by [miscFileType]
 */
internal class PyMiscFileAction(private val miscFileType: MiscFileType) : AnAction(
  miscFileType.title,
  null,
  miscFileType.icon
) {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
  private val projectPath = Path.of(SystemProperties.getUserHome()).resolve("PyCharmMiscProject")


  @RequiresEdt
  override fun actionPerformed(e: AnActionEvent) {
    when (val r = createMiscProject(projectPath, miscFileType, { it.service<MyService>().scope })) {
      is Result.Success -> Unit
      is Result.Failure -> {
        Messages.showErrorDialog(null as Project?, r.error.text, PyCharmCommunityCustomizationBundle.message("misc.project.error.title"))
      }
    }
  }
}

@Service(Service.Level.PROJECT)
private class MyService(val scope: CoroutineScope)
