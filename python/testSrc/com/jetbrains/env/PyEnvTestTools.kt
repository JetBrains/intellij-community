// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.env

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.replaceService
import com.jetbrains.python.testing.VFSTestFrameworkListener


/**
 * Async/timer services are useless in tests and also may lead to dispose problems when project is disposed before FS changes are processed
 */
internal fun replaceServicesWithMocks(disposable: Disposable) {
  ApplicationManager.getApplication().replaceService(VFSTestFrameworkListener::class.java, object : VFSTestFrameworkListener {
    override fun updateAllTestFrameworks(sdk: Sdk) {
    }

    override fun isTestFrameworkInstalled(sdk: Sdk?, name: String): Boolean {
      return true
    }

    override fun setTestFrameworkInstalled(installed: Boolean, sdkHome: String, name: String) {
    }
  }, disposable)
}
