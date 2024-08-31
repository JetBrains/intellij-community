// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Disposer
import com.intellij.platform.ModuleAttachListener
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import java.nio.file.Path

class PyPackagesToolWindowModuleAttachListener : ModuleAttachListener {
  override fun afterAttach(module: Module, primaryModule: Module?, imlFile: Path, tasks: MutableList<suspend () -> Unit>) {
    tasks.add {
      module.project.service<PyPackagingToolWindowService>().moduleAttached()
    }
  }

  override fun beforeDetach(module: Module) {
    @Suppress("IncorrectParentDisposable")
    //We do not have function after detach so this is small trick
    Disposer.register(module, Disposable {
      PyPackageCoroutine.launch(module.project) {
        module.project.service<PyPackagingToolWindowService>().moduleAttached()
      }
    })

  }
}