// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.configuration.suppressors

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.jetbrains.python.inspections.PyInspectionExtension
import com.jetbrains.python.psi.PyFile

internal class PyInterpreterInspectionSuppressor : PyInspectionExtension() {
  override fun ignoreInterpreterWarnings(file: PyFile): Boolean = suppress

  private class Suppressor : Disposable {
    init {
      suppress = true
      thisLogger().info("Interpreter warnings have been disabled")
    }

    override fun dispose() {
      suppress = false
      thisLogger().info("Interpreter warnings have been enabled")
    }

  }


  @Suppress("CompanionObjectInExtension")
  companion object {
    private var suppress = false

    fun suppress(project: Project): Disposable? {
      DaemonCodeAnalyzer.getInstance(project).restart()
      return if (suppress) null else Suppressor()
    }
  }
}