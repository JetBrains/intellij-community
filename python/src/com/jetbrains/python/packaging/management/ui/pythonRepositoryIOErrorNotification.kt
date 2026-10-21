// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.management.ui

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.cache.PythonPackageCacheIOError
import com.jetbrains.python.packaging.management.PythonRepositoryManager.PythonRepositoryIOError

internal fun PythonRepositoryIOError.notify(project: Project) =
  errorNotification(project, message)

internal fun PythonPackageCacheIOError.notify(project: Project) =
  errorNotification(project, message)

private fun errorNotification(project: Project, message: @NlsSafe String) {
  Notification(
    "PythonPackages",
    PyBundle.message("python.packaging.cache.io.error", message),
    NotificationType.ERROR
  ).notify(project)
}