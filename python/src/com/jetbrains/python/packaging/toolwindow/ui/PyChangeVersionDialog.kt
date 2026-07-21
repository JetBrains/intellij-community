// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.python.processOutput.common.ProcessOutputQuery
import com.intellij.python.processOutput.common.sendProcessOutputQuery
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.EmptyIcon
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.TraceContext
import com.jetbrains.python.packaging.common.PythonPackageDetails
import com.jetbrains.python.packaging.management.PyPackageScope
import com.jetbrains.python.packaging.management.toInstallRequest
import com.jetbrains.python.packaging.toolwindow.PyPackagingToolWindowService
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import kotlinx.coroutines.Dispatchers

internal fun showChangeVersionPopup(
  project: Project,
  details: PythonPackageDetails,
  scope: PyPackageScope = PyPackageScope.NONE,
  anchor: RelativePoint? = null,
  highlightVersion: String? = null,
  currentVersion: String? = null,
) {
  val versions = details.availableVersions
  if (versions.isEmpty()) return

  val emptyCheck = EmptyIcon.create(AllIcons.Actions.Checked)
  val popup = buildVersionChooserPopup(
    items = versions,
    iconFor = { v -> if (v == currentVersion) AllIcons.Actions.Checked else emptyCheck },
  ) { selectedValue ->
    val specification = details.toPackageSpecification(selectedValue)
    if (specification == null) {
      val trace = TraceContext(message("python.packaging.change.version.dialog.title"), null)
      PyPackageCoroutine.launch(project, Dispatchers.IO) {
        sendProcessOutputQuery(ProcessOutputQuery.OpenToolWindowByTraceUuid(trace.uuid.toString()))
      }
      return@buildVersionChooserPopup
    }
    PyPackageCoroutine.launch(project, Dispatchers.IO) {
      PyPackagingToolWindowService.getInstance(project)
        .installPackage(specification.toInstallRequest(), workspaceMember = scope.workspaceMember, dependencyGroup = scope.dependencyGroup)
    }
  }
  if (anchor != null) popup.show(anchor) else popup.showInFocusCenter()
}
