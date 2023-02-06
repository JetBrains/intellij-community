// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow

import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.platform.ModuleAttachListener
import java.nio.file.Path

class PyPackagesToolWindowModuleAttachListener : ModuleAttachListener {
  override fun afterAttach(module: Module, primaryModule: Module?, imlFile: Path, tasks: MutableList<suspend () -> Unit>) {
    tasks.add {
      module.project.service<PyPackagingToolWindowService>().moduleAttached()
    }
  }
}