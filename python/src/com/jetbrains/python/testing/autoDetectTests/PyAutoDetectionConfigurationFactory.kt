// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.testing.autoDetectTests

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.PyBundle
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

  fun getFactory(sdk: Sdk): PyAbstractTestFactory<*> =
    factoriesExcludingThis.firstOrNull { it.isFrameworkInstalled(sdk) } ?: PyUnitTestFactory(type)

  override fun createTemplateConfiguration(project: Project): PyAutoDetectTestConfiguration =
    PyAutoDetectTestConfiguration(project, this)

  override fun onlyClassesAreSupported(sdk: Sdk): Boolean = getFactory(sdk).onlyClassesAreSupported(sdk)

  override fun getId(): String = "Autodetect"

  override fun getName(): String = PyBundle.message("runcfg.autodetect.display_name")
}
