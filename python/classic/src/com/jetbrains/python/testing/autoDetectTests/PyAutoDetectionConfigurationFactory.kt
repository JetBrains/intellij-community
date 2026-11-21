// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.testing.autoDetectTests

import com.intellij.openapi.project.Project
import com.intellij.python.sdk.backend.PythonInterpreter
import com.jetbrains.python.PyBundle
import com.jetbrains.python.testing.PyAbstractTestFactory
import com.jetbrains.python.testing.PyUnitTestFactory
import com.jetbrains.python.testing.PythonTestConfigurationType
import org.jetbrains.annotations.ApiStatus


class PyAutoDetectionConfigurationFactory(private val type: PythonTestConfigurationType) : PyAbstractTestFactory<PyAutoDetectTestConfiguration>(
  type) {

  override val isExplicitChoice: Boolean get() = false
  internal companion object {
    val factoriesExcludingThis: Collection<PyAbstractTestFactory<*>>
      get() =
        PythonTestConfigurationType.getInstance().typedFactories.toTypedArray().filterNot { it is PyAutoDetectionConfigurationFactory }
  }

  @ApiStatus.Internal
  fun getFactory(interpreter: PythonInterpreter, project: Project): PyAbstractTestFactory<*> =
    factoriesExcludingThis.firstOrNull { it.isFrameworkInstalled(project, interpreter) } ?: PyUnitTestFactory(type)

  override fun createTemplateConfiguration(project: Project): PyAutoDetectTestConfiguration =
    PyAutoDetectTestConfiguration(project, this)

  @ApiStatus.Internal
  override fun onlyClassesAreSupported(project: Project, interpreter: PythonInterpreter): Boolean =
    getFactory(interpreter, project).onlyClassesAreSupported(project, interpreter)

  override fun getId(): String = "Autodetect"

  override fun getName(): String = PyBundle.message("runcfg.autodetect.display_name")
}
