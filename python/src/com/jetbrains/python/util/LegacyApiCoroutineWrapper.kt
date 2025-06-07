// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.util

import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import kotlinx.coroutines.CoroutineScope
import java.awt.EventQueue.isDispatchThread

// In case legacy sync API can be called from both EDT and background. This wrapper automatically chooses the appropriate way to launch it.
fun <T> runWithModalBlockingOrInBackground(project: Project, @NlsSafe msg: String, action: suspend CoroutineScope.() -> T): T {
  if (isDispatchThread()) {
    return runWithModalProgressBlocking(project, msg, action)
  }

  return runBlockingCancellable(action)
}