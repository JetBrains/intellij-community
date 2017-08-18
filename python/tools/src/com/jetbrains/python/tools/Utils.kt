package com.jetbrains.python.tools

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.util.ui.UIUtil
import com.jetbrains.python.sdk.PythonSdkType
import com.jetbrains.python.sdk.PythonSdkUpdater
import com.jetbrains.python.tools.sdkTools.PySdkTools
import com.jetbrains.python.tools.sdkTools.SdkCreationType
import java.io.File

internal val TestPath = System.getenv("PYCHARM_PERF_ENVS")

@JvmOverloads
fun createSdkForPerformance(module: Module,
                            sdkCreationType: SdkCreationType,
                            sdkHome: String = File(TestPath, "envs/py36_64").absolutePath): Sdk {
  ApplicationInfoImpl.setInStressTest(true) // To disable slow debugging
  val executable = File(PythonSdkType.getPythonExecutable(sdkHome) ?: throw AssertionError("No python on $sdkHome"))
  println("Running on $sdkHome")
  return PySdkTools.createTempSdk(VfsUtil.findFileByIoFile(executable, true)!!, sdkCreationType, module)
}


fun openProjectWithSdk(projectPath: String, sdkHome: String): Pair<Project?, Sdk?> {
  val project: Project? = ProjectManager.getInstance().loadAndOpenProject(projectPath)

  val module = ModuleManager.getInstance(project!!).modules[0]

  val sdk = createSdkForPerformance(module, SdkCreationType.SDK_PACKAGES_AND_SKELETONS, sdkHome)

  UIUtil.invokeAndWaitIfNeeded(Runnable {
    ApplicationManager.getApplication().runWriteAction({
                                                         PythonSdkUpdater.update(sdk, null, project, null)
                                                       })
  })

  if (module != null) {
    ModuleRootModificationUtil.setModuleSdk(module, sdk)
  }

  assert(ModuleRootManager.getInstance(module).orderEntries().classesRoots.size > 5)
  assert(ModuleManager.getInstance(project).modules.size == 1)

  return Pair(project, sdk)
}