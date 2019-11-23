// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.index.stubs

import com.intellij.idea.IdeaTestApplication
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ui.UIUtil
import java.io.File
import kotlin.system.exitProcess

abstract class ProjectSdkStubsGenerator {
  open fun createStubsGenerator(stubsFilePath: String): StubsGenerator = StubsGenerator("", stubsFilePath)

  abstract val moduleTypeId: String

  abstract fun createSdkProducer(sdkPath: String): (Project, Module) -> Sdk

  open val root: String? = System.getenv("SDK_ROOT")

  private val stubsFileName = "sdk-stubs"

  fun buildStubs(baseDir: String) {
    val app = IdeaTestApplication.getInstance()


    try {
      for (python in File(root).listFiles()) {
        if (python.name.startsWith(".")) {
          continue
        }
        indexSdkAndStoreSerializedStubs("${PathManager.getHomePath()}/python/testData/empty",
                                        python.absolutePath,
                                        "$baseDir/$stubsFileName")
      }
    }
    catch (e: Throwable) {
      e.printStackTrace()
    }
    finally {
      app.disposeApp()
      exitProcess(0) //TODO: graceful shutdown
    }
  }

  private fun indexSdkAndStoreSerializedStubs(projectPath: String, sdkPath: String, stubsFilePath: String) {
    val pair = openProjectWithSdk(projectPath, moduleTypeId, createSdkProducer(sdkPath))

    val project = pair.first
    val sdk = pair.second

    try {

      val roots: List<VirtualFile> = sdk!!.rootProvider.getFiles(OrderRootType.CLASSES).asList()

      val stubsGenerator = createStubsGenerator(stubsFilePath)

      stubsGenerator.buildStubsForRoots(roots)
    }
    finally {
      UIUtil.invokeAndWaitIfNeeded(Runnable {
        ProjectManager.getInstance().closeProject(project)
        WriteAction.run<Throwable> {
          Disposer.dispose(project)
          SdkConfigurationUtil.removeSdk(sdk!!)
        }
      })
    }
  }
}