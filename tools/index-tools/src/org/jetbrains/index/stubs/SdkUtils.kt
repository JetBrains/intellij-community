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

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.util.ui.UIUtil
import java.io.File

/**
 * @author traff
 */

fun openProjectWithSdk(projectPath: String,
                       moduleTypeId: String,
                       sdkProducer: (Project, Module) -> Sdk?): Pair<Project?, Sdk?> {
  println("Opening project at $projectPath")

  val project: Project? = ProjectManager.getInstance().loadAndOpenProject(projectPath)

  try {
    val module = getOrCreateModule(project!!, projectPath, moduleTypeId)

    val sdk = sdkProducer(project, module)

    ModuleRootModificationUtil.setModuleSdk(module, sdk)

    assert(ModuleRootManager.getInstance(module).orderEntries().classesRoots.size > 5)

    assert(ModuleManager.getInstance(project).modules.size == 1)

    return Pair(project, sdk)
  }
  catch (e: Throwable) {
    if (project != null) {
      UIUtil.invokeAndWaitIfNeeded(Runnable {
        ProjectManager.getInstance().closeProject(project)
        WriteAction.run<Throwable> {
          Disposer.dispose(project)
        }
      })
    }
    throw e
  }
}

fun getOrCreateModule(project: Project, projectPath: String, moduleTypeId: String): Module {
  if (ModuleManager.getInstance(project).modules.isNotEmpty()) {
    return ModuleManager.getInstance(project).modules[0]
  }
  else {
    val module: Module = ApplicationManager.getApplication().runWriteAction(
      Computable<Module> { ModuleManager.getInstance(project).newModule(projectPath, moduleTypeId) }
    )

    val root = VfsUtil.findFileByIoFile(File(projectPath), true)!!

    ModuleRootModificationUtil.updateModel(module, { t ->
      val e = t.addContentEntry(root)
      e.addSourceFolder(root, false)
    })

    return module
  }
}