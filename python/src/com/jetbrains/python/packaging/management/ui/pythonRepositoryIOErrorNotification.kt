// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.management.ui

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.management.PythonRepositoryManager.PythonRepositoryIOError

internal fun PythonRepositoryIOError.notify(project: Project) {
  Notification(
    "PythonPackages",
    PyBundle.message("python.packaging.cache.io.error", message),
    NotificationType.ERROR
  ).notify(project)
}