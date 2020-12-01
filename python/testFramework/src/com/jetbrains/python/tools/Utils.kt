// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.tools

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.util.ui.UIUtil
import com.jetbrains.python.PyNames
import com.jetbrains.python.sdk.PythonSdkUpdater
import com.jetbrains.python.sdk.PythonSdkUtil
import com.jetbrains.python.tools.sdkTools.PySdkTools
import com.jetbrains.python.tools.sdkTools.SdkCreationType
import org.jetbrains.index.stubs.openProjectWithSdk
import java.io.File

internal val TestPath = System.getenv("PYCHARM_PERF_ENVS")

@JvmOverloads
fun createSdkForPerformance(module: Module,
                            sdkCreationType: SdkCreationType,
                            sdkHome: String = File(TestPath, "envs/py36_64").absolutePath): Sdk {
  ApplicationInfoImpl.setInStressTest(true) // To disable slow debugging
  val executable = File(PythonSdkUtil.getPythonExecutable(sdkHome) ?: throw AssertionError("No python on $sdkHome"))
  println("Creating Python SDK $sdkHome")
  return PySdkTools.createTempSdk(VfsUtil.findFileByIoFile(executable, true)!!, sdkCreationType, module)
}


fun openProjectWithPythonSdk(projectPath: String, sdkHome: String?, testRootDisposable: Disposable): Pair<Project, Sdk?> {
  val sdkProducer = if (sdkHome == null) {_,_->null} else createPythonSdkProducer(sdkHome)
  return openProjectWithSdk(projectPath, PyNames.PYTHON_MODULE_ID, sdkProducer, testRootDisposable)
}

fun createPythonSdkProducer(sdkHome: String): (Project, Module) -> Sdk {
  return { project: Project, module: Module ->
    run {
      val sdk = createSdkForPerformance(module, SdkCreationType.SDK_PACKAGES_AND_SKELETONS, sdkHome)
      UIUtil.invokeAndWaitIfNeeded(Runnable {
        PythonSdkUpdater.update(sdk, project, null)
      })
      sdk
    }
  }
}

