package com.jetbrains.python.module

import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.platform.DirectoryProjectGenerator
import com.jetbrains.python.PythonMockSdk
import com.jetbrains.python.fixtures.PyTestCase

class MockPyModuleService: PyModuleService() {
  private val mySdk = PythonMockSdk.create(PyTestCase.PYTHON_3_MOCK_SDK)

  override fun findPythonSdk(module: Module): Sdk? = mySdk

  override fun createPythonModuleBuilder(generator: DirectoryProjectGenerator<*>?): ModuleBuilder = throw UnsupportedOperationException()
}