// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.getSdkAPI
import com.jetbrains.python.sdk.setAssociationToModule

/** Associates [interpreter] with [module]. The association is data of the SDK, so the call edits the SDK. */
internal fun setAssociationToModuleAsync(interpreter: PythonInterpreter, module: Module) {
  ApplicationManager.getApplication().invokeAndWait {
    runWithModalProgressBlocking(module.project, "D") {
      @Suppress("DEPRECATION")
      interpreter.getSdkAPI().setAssociationToModule(module)
    }
  }
}
