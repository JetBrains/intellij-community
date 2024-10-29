// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.tools

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.ui.UIUtil
import com.jetbrains.python.PyNames
import com.jetbrains.python.packaging.PyPackagingSettings
import com.jetbrains.python.sdk.PythonSdkUpdater
import com.jetbrains.python.sdk.PythonSdkUtil
import com.jetbrains.python.sdk.VirtualEnvReader
import com.jetbrains.python.tools.sdkTools.PySdkTools
import com.jetbrains.python.tools.sdkTools.SdkCreationType
import java.io.File
import java.nio.file.Paths
import kotlin.io.path.Path

internal val TestPath = System.getenv("PYCHARM_PERF_ENVS")

@JvmOverloads
fun createSdkForPerformance(module: Module,
                            sdkCreationType: SdkCreationType,
                            sdkHome: String = File(TestPath, "envs/py36_64").absolutePath): Sdk {
  ApplicationManagerEx.setInStressTest(true)
  // To disable slow debugging
  val executable = VirtualEnvReader.Instance.findPythonInPythonRoot(Path(sdkHome))?.toFile() ?: throw AssertionError("No python on $sdkHome")
  println("Creating Python SDK $sdkHome")
  return PySdkTools.createTempSdk(VfsUtil.findFileByIoFile(executable, true)!!, sdkCreationType, module,
                                  PyPackagingSettings.getInstance(module.project))
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

fun openProjectWithSdk(projectPath: String,
                       moduleTypeId: String,
                       sdkProducer: (Project, Module) -> Sdk?,
                       testRootDisposable: Disposable): Pair<Project, Sdk?> {
  println("Opening project at $projectPath")

  val project = PlatformTestUtil.loadAndOpenProject(Paths.get(projectPath), testRootDisposable)
  try {
    val module = getOrCreateModule(project, projectPath, moduleTypeId)

    val sdk = sdkProducer(project, module)

    ModuleRootModificationUtil.setModuleSdk(module, sdk)

    if (sdk != null) {
      assert(ModuleRootManager.getInstance(module).orderEntries().classesRoots.isNotEmpty())
    }

    assert(ModuleManager.getInstance(project).modules.size == 1)

    return Pair(project, sdk)
  }
  catch (e: Throwable) {
    PlatformTestUtil.forceCloseProjectWithoutSaving(project)
    throw e
  }
}

fun getOrCreateModule(project: Project, projectPath: String, moduleTypeId: String): Module {
  var module = ModuleManager.getInstance(project).modules.firstOrNull()
  if (module == null) {
    module = runWriteAction {
      ModuleManager.getInstance(project).newModule(projectPath, moduleTypeId)
    }

    val root = VfsUtil.findFileByIoFile(File(projectPath), true) ?: throw AssertionError("Can't find $projectPath")

    ModuleRootModificationUtil.updateModel(module) { t ->
      val e = t.addContentEntry(root)
      e.addSourceFolder(root, false)
    }
  }

  return module
}

