// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.toolwindow.impl

import com.intellij.execution.TerminateRemoteProcessDialog
import com.intellij.execution.TerminateRemoteProcessDialog.ProcessCloseConfirmationResult
import com.intellij.execution.process.NopProcessHandler
import com.intellij.execution.ui.RunContentManagerImpl
import com.intellij.openapi.project.Project

/**
 * Shows a single confirmation dialog listing all [tabTitles] with running processes.
 *
 * @return `true` if the user agreed to terminate the processes, `false` if they chose to leave them running.
 */
internal fun confirmTermination(project: Project, tabTitles: List<String>): Boolean {
  val fakeProcesses = List(tabTitles.size) {
    NopProcessHandler().also {
      it.startNotify()
      it.putUserData(RunContentManagerImpl.ALWAYS_USE_DEFAULT_STOPPING_BEHAVIOUR_KEY, true)
    }
  }
  return TerminateRemoteProcessDialog.show(project, tabTitles, fakeProcesses) != ProcessCloseConfirmationResult.LEAVE_RUNNING
}
