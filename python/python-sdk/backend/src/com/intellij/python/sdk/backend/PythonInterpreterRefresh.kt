// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.sdk.backend

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.newvfs.RefreshQueue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
suspend fun refreshPaths(project: Project, interpreter: PythonInterpreter): Unit = withContext(Dispatchers.IO) {
  // Background refreshing breaks structured concurrency: there is a some activity in background that locks files.
  // Temporary folders can't be deleted on Windows due to that.
  // That breaks tests.
  // This code should be deleted, but disabled temporary to fix tests
  if (!(ApplicationManager.getApplication().isUnitTestMode && SystemInfoRt.isWindows)) {
    val files = interpreter.classRoots(project)
    VfsUtil.markDirty(true, true, *files)
    RefreshQueue.getInstance().refresh(true, files.toList())
    RefreshQueue.getInstance().refresh(false, listOfNotNull(interpreter.associatedModuleDir))
  }
  else {
    RefreshQueue.getInstance().refresh(true, listOfNotNull(interpreter.sitePackagesDirectory(project), interpreter.associatedModuleDir))
  }

  PythonInterpreterUpdater.getInstance(project).request(interpreter, PythonInterpreterUpdateRequest(packages = false, background = false))
}