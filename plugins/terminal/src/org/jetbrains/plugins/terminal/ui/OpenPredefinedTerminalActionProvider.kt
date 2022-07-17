// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal.ui

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLockAbsence

interface OpenPredefinedTerminalActionProvider {

  @RequiresBackgroundThread
  @RequiresReadLockAbsence
  fun listOpenPredefinedTerminalActions(project: Project): List<AnAction>

  companion object {
    val EP_NAME = ExtensionPointName.create<OpenPredefinedTerminalActionProvider>(
      "org.jetbrains.plugins.terminal.openPredefinedTerminalProvider")

    @JvmStatic
    @RequiresBackgroundThread
    @RequiresReadLockAbsence
    fun collectAll(project: Project): List<AnAction> = EP_NAME.extensionList.flatMap {
      it.listOpenPredefinedTerminalActions(project)
    }
  }
}