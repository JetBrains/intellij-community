// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.packaging

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys


class PySyncPythonRequirementsAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val module = e.getData(LangDataKeys.MODULE) ?: return
    syncWithImports(module)
  }
}