// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.systemPython.impl

import com.intellij.ide.ApplicationInitializedListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.python.community.services.systemPython.SystemPythonService
import java.nio.file.FileSystemException
import kotlin.io.path.pathString

internal class SystemPythonRootsFixer : ApplicationInitializedListener {
  override suspend fun execute() {
    val disposable = ApplicationManager.getApplication()
    val pythonDirs = SystemPythonService()
      .findSystemPythons()
      .map {
        try {
          // It might throw FSE on Windows for pythons installed from store, see WinAppxTools
          it.pythonBinary.toRealPath()
        }
        catch (_: FileSystemException) {
          it.pythonBinary
        }.parent.pathString
      }
      .toTypedArray()
    VfsRootAccess.allowRootAccess(disposable, *pythonDirs)
  }
}