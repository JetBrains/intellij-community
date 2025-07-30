// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.stubs.checkers

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Key
import com.jetbrains.python.packaging.common.PythonPackageManagementListener
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import com.jetbrains.python.sdk.PythonSdkUtil
import com.jetbrains.python.statistics.sdks

internal abstract class PyStubsChecker(val project: Project) : Disposable.Default {
  val key: Key<Set<PyPackageStubLink>> = Key(this::class.java.name)

  init {
    project.messageBus.connect(this).subscribe(PythonPackageManager.PACKAGE_MANAGEMENT_TOPIC, object : PythonPackageManagementListener {
      override fun packagesChanged(sdk: Sdk) {
        PyPackageCoroutine.launch(project) {
          checkSdk(sdk)
        }
      }

    })

    project.sdks.filter { PythonSdkUtil.isPythonSdk(it) }.forEach { startCheckForSdk(it) }
  }

  fun getCached(sdk: Sdk): Set<PyPackageStubLink> = sdk.getUserData(key) ?: emptySet()

  protected abstract suspend fun detectSuggestedStubs(packageManager: PythonPackageManager): List<PyPackageStubLink>


  private fun startCheckForSdk(sdk: Sdk) {
    if (sdk.getUserData(key) != null) return

    if (ApplicationManager.getApplication().isUnitTestMode) {
      runBlockingMaybeCancellable {
        checkSdk(sdk)
      }
    }
    else {
      PyPackageCoroutine.launch(project) {
        checkSdk(sdk)
      }
    }
  }

  private suspend fun checkSdk(sdk: Sdk) {
    val suggested = detectSuggestedStubs(PythonPackageManager.forSdk(project, sdk)).toSet()
    sdk.putUserData(key, suggested)
  }
}