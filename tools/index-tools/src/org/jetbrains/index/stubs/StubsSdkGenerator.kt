/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

/**
 * @author traff
 */

abstract class ProjectSdkStubsGenerator {
  open fun createStubsGenerator() = StubsGenerator("")

  abstract val moduleTypeId: String

  abstract fun createSdkProducer(sdkPath: String): (Project, Module) -> Sdk?

  open val root = System.getenv("SDK_ROOT")

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
      UIUtil.invokeAndWaitIfNeeded(Runnable {
        WriteAction.run<Throwable> {
          app.dispose()
        }
      })
      System.exit(0) //TODO: graceful shutdown
    }
  }

  fun indexSdkAndStoreSerializedStubs(projectPath: String, sdkPath: String, stubsFilePath: String) {
    val pair = openProjectWithSdk(projectPath, moduleTypeId, createSdkProducer(sdkPath))

    val project = pair.first
    val sdk = pair.second

    try {

      val roots: List<VirtualFile> = sdk!!.rootProvider.getFiles(OrderRootType.CLASSES).asList()

      val stubsGenerator = createStubsGenerator()

      stubsGenerator.buildStubsForRoots(stubsFilePath, roots)
    }
    finally {
      UIUtil.invokeAndWaitIfNeeded(Runnable {
        ProjectManager.getInstance().closeProject(project!!)
        WriteAction.run<Throwable> {
          Disposer.dispose(project)
          SdkConfigurationUtil.removeSdk(sdk)
        }
      })
    }
  }
}