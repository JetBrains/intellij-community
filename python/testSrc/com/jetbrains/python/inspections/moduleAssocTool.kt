// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.jetbrains.python.sdk.setAssociationToModule

internal fun setAssociationToModuleAsync(sdk: Sdk, module: Module) {
  ApplicationManager.getApplication().invokeAndWait {
    runWithModalProgressBlocking(module.project, "D") {
      sdk.setAssociationToModule(module)
    }
  }
}
