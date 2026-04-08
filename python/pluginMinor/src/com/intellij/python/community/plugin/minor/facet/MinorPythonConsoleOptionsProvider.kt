// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.plugin.minor.facet

import com.intellij.facet.FacetManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessModuleDir
import com.jetbrains.python.console.PyConsoleOptions
import com.jetbrains.python.console.PyConsoleOptionsProvider
import com.jetbrains.python.console.PyConsoleParameters
import com.jetbrains.python.console.PyConsoleType
import org.jetbrains.annotations.ApiStatus

/**
 * [PyConsoleOptionsProvider] for mini-IDE capable IDEs (e.g. CLion) that store the
 * Python SDK in a [MinorPythonFacet] rather than a module-level SDK.
 *
 * This provider bridges [MinorPythonFacet]-based SDK configuration with the
 * Python console infrastructure. It does NOT add a settings tab (see
 * [isApplicableTo]) but returns proper [PyConsoleParameters] for modules that
 * have a configured Python interpreter so that
 * [com.intellij.python.pro.sdk.FrameworkAwarePythonConsoleRunnerFactory] can
 * resolve the correct SDK without falling through to an unrelated global SDK.
 */
@ApiStatus.Internal
class MinorPythonConsoleOptionsProvider : PyConsoleOptionsProvider {

  /**
   * Returns false — the base Python console settings tab already covers this
   * case, so no additional tab should appear in Settings | Python Console.
   */
  override fun isApplicableTo(project: Project): Boolean = false
  
  @Suppress("HardCodedStringLiteral")
  override fun getName(): String = "Python" // It is not visible to the user, but it is used in the console settings.  

  override fun getHelpTopic(): String = ""

  override fun getSettings(project: Project): PyConsoleOptions.PyConsoleSettings =
    PyConsoleOptions.getInstance(project).pythonConsoleSettings

  /**
   * Returns [PyConsoleParameters] for a module that has a [MinorPythonFacet]
   * with a configured SDK, or `null` if this module is not a mini-IDE Python
   * module.  A non-null return value guarantees that
   * [com.jetbrains.python.sdk.legacy.PythonSdkUtil.findPythonSdk] will
   * succeed for the returned module (the facet SDK is not null).
   */
  override fun getConsoleParameters(module: Module): PyConsoleParameters? {
    val facet = FacetManager.getInstance(module).getFacetByType(MinorPythonFacet.ID) ?: return null
    // Only provide parameters when an SDK is actually configured; otherwise
    // fall through to the default SDK-search logic in the base factory.
    facet.configuration.sdk ?: return null

    val projectRoot = module.guessModuleDir()?.path ?: return null

    return PyConsoleParameters(
      module = module,
      projectRoot = projectRoot,
      consoleSettings = PyConsoleOptions.getInstance(module.project).pythonConsoleSettings,
      consoleType = PyConsoleType.PYTHON,
    )
  }

  override fun customizeEnvVariables(module: Module, environmentVariablesToBeCustomized: MutableMap<String, String>) {
    // No framework-specific environment variables needed for the mini-IDE Python console.
  }
}
