// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.miscProject.impl

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.pycharm.community.ide.impl.miscProject.MiscFileType

internal class PyMiscFileActionGroup : ActionGroup() {

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = miscProjectEnabled.value
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun getChildren(e: AnActionEvent?): Array<out AnAction> =
    if (miscProjectEnabled.value)
      (MiscFileType.EP.extensionList + listOf(MiscScriptFileType)).map { PyMiscFileAction(it) }.toTypedArray()
    else
      emptyArray()
}