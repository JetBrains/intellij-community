// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.restructuredtext.python

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.module.ModuleManager.Companion.getInstance
import com.jetbrains.python.psi.resolve.PackageAvailabilitySpec
import com.jetbrains.python.psi.resolve.isPackageAvailable

/**
 * User : catherine
 */
object RestPythonUtil {
  private val SPHINX_MARKER = PackageAvailabilitySpec("Sphinx", "sphinx.application.Sphinx")

  @JvmStatic
  fun updateSphinxQuickStartRequiredAction(e: AnActionEvent): Presentation {
    val presentation = e.presentation

    val module = e.getData(PlatformCoreDataKeys.MODULE)
                 ?: e.getData(CommonDataKeys.PROJECT)?.let { getInstance(it).modules.firstOrNull() }
                 ?: return presentation

    presentation.isEnabled = isPackageAvailable(module, SPHINX_MARKER)
    return presentation
  }
}