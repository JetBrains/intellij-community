// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.management

import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.openapi.project.Project
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.getSdkAPI
import com.jetbrains.python.tools.sdkTools.PythonMockPackageManagerProvider
import com.jetbrains.python.tools.sdkTools.PythonMockSdkKeys
import org.jetbrains.annotations.TestOnly

/**
 * Gives a mock SDK of a test a [TestPythonPackageManager]: a mock SDK cannot run, so no real manager can read its
 * packages. A test then installs packages with [TestPythonPackageManager.withPackageInstalled]. Any other SDK gets the
 * manager of the providers after this one.
 */
@TestOnly
internal class MockSdkPackageManagerProvider : PythonPackageManagerProvider {
  override fun createPackageManager(project: Project, interpreter: PythonInterpreter): PythonPackageManager? =
    if (PythonMockSdkKeys.isMock(interpreter.getSdkAPI())) TestPythonPackageManager(project, interpreter) else null

  companion object {
    /**
     * Puts this provider before the others while [project] lives, also before the empty manager of a mock, see
     * [PythonMockPackageManagerProvider]. A light project calls it once, before it builds a manager, see
     * `PyLightProjectDescriptor`.
     */
    @JvmStatic
    fun register(project: Project) {
      PythonMockPackageManagerProvider.yieldTo(project)
      PythonPackageManagerProvider.EP_NAME.point.registerExtension(MockSdkPackageManagerProvider(), LoadingOrder.FIRST, project)
    }
  }
}
