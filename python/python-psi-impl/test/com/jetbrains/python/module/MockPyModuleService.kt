package com.jetbrains.python.module

import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.PythonMockSdk

val PYTHON_3_MOCK_SDK = "3.7"

class MockPyModuleService : PyModuleService() {
  private val mySdk = PythonMockSdk.create(PYTHON_3_MOCK_SDK)

  override fun findPythonSdk(module: Module): Sdk? = mySdk
}
