package com.jetbrains.python.sdk.poetry

import com.intellij.openapi.project.Project
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.getSdkAPI
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.PythonPackageManagerProvider

/**
 *  This source code is created by @koxudaxi Koudai Aono <koxudaxi@gmail.com>
 */

internal class PoetryPackageManagerProvider : PythonPackageManagerProvider {
  // The manager constructor still takes the SDK.
  @Suppress("DEPRECATION")
  override fun createPackageManager(project: Project, interpreter: PythonInterpreter): PythonPackageManager? =
    if (interpreter.isPoetry) PoetryPackageManager(project, interpreter.getSdkAPI()) else null
}
