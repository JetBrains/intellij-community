// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.restructuredtext.python

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.module.ModuleManager.Companion.getInstance
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.hasInstalledPackageSnapshot
import com.jetbrains.python.sdk.PythonSdkUtil

/**
 * User : catherine
 */
object RestPythonUtil {
  @JvmStatic
  fun updateSphinxQuickStartRequiredAction(e: AnActionEvent): Presentation {
    val presentation = e.presentation

    val project = e.getData(CommonDataKeys.PROJECT) ?: return presentation
    val module = e.getData(PlatformCoreDataKeys.MODULE)
                 ?: getInstance(project).modules.firstOrNull()
                 ?: return presentation

    val sdk = PythonSdkUtil.findPythonSdk(module) ?: return presentation
    val packageManager = PythonPackageManager.forSdk(project, sdk)
    presentation.isEnabled = packageManager.hasInstalledPackageSnapshot("Sphinx")
    return presentation
  }
}