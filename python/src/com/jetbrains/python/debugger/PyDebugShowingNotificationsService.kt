// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service

@Service(Service.Level.APP)
class PyDebugShowingNotificationsService {
  var isCythonNotificationShown: Boolean = false

  companion object {
    @JvmStatic
    fun getInstance(): PyDebugShowingNotificationsService = service()
  }
}