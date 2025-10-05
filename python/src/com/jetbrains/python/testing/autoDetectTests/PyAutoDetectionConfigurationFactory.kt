// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.testing.autoDetectTests

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.PyBundle
import com.jetbrains.python.sdk.pythonSdk
import com.jetbrains.python.testing.PyAbstractTestFactory
import com.jetbrains.python.testing.PyUnitTestFactory
import com.jetbrains.python.testing.PythonTestConfigurationType


class PyAutoDetectionConfigurationFactory(private val type: PythonTestConfigurationType) : PyAbstractTestFactory<PyAutoDetectTestConfiguration>(
  type) {
  internal companion object {
    val factoriesExcludingThis: Collection<PyAbstractTestFactory<*>>
      get() =
        PythonTestConfigurationType.getInstance().typedFactories.toTypedArray().filterNot { it is PyAutoDetectionConfigurationFactory }
  }


  @Deprecated("Use getFactory(sdk, project)", ReplaceWith("getFactory(sdk, project)"), DeprecationLevel.ERROR)
  fun getFactory(sdk: Sdk): PyAbstractTestFactory<*> {
    val project = ProjectManager.getInstance().openProjects.firstOrNull { sdk == it.pythonSdk }!!
    return factoriesExcludingThis.firstOrNull { it.isFrameworkInstalled(project, sdk) } ?: PyUnitTestFactory(type)
  }


  fun getFactory(sdk: Sdk, project: Project): PyAbstractTestFactory<*> =
    factoriesExcludingThis.firstOrNull { it.isFrameworkInstalled(project, sdk) } ?: PyUnitTestFactory(type)

  override fun createTemplateConfiguration(project: Project): PyAutoDetectTestConfiguration =
    PyAutoDetectTestConfiguration(project, this)

  override fun onlyClassesAreSupported(project: Project, sdk: Sdk): Boolean = getFactory(sdk, project).onlyClassesAreSupported(project, sdk)

  override fun getId(): String = "Autodetect"

  override fun getName(): String = PyBundle.message("runcfg.autodetect.display_name")
}
