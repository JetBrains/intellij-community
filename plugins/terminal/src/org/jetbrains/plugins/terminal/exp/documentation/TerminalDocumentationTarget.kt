// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp.documentation

import com.intellij.model.Pointer
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.presentation.TargetPresentation
import org.jetbrains.annotations.Nls

internal class TerminalDocumentationTarget(
  private val name: @NlsSafe String,
  private val description: @Nls String
) : DocumentationTarget {
  override fun createPointer(): Pointer<out TerminalDocumentationTarget> {
    return Pointer.hardPointer(this)
  }

  override fun computePresentation(): TargetPresentation {
    return TargetPresentation.builder(name).presentation()
  }

  override fun computeDocumentation(): DocumentationResult {
    return DocumentationResult.documentation(description)
  }
}
