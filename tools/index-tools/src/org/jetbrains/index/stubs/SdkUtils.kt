// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.index.stubs

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.PlatformTestUtil
import java.io.File
import java.nio.file.Paths

fun openProjectWithSdk(projectPath: String,
                       moduleTypeId: String,
                       sdkProducer: (Project, Module) -> Sdk?): Pair<Project, Sdk?> {
  println("Opening project at $projectPath")

  val project = PlatformTestUtil.loadAndOpenProject(Paths.get(projectPath))
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
  if (ModuleManager.getInstance(project).modules.isNotEmpty()) {
    return ModuleManager.getInstance(project).modules[0]
  }
  else {
    val module: Module = ApplicationManager.getApplication().runWriteAction(
      Computable { ModuleManager.getInstance(project).newModule(projectPath, moduleTypeId) }
    )

    val root = VfsUtil.findFileByIoFile(File(projectPath), true) ?: throw AssertionError("Can't find $projectPath")

    ModuleRootModificationUtil.updateModel(module) { t ->
      val e = t.addContentEntry(root)
      e.addSourceFolder(root, false)
    }

    return module
  }
}