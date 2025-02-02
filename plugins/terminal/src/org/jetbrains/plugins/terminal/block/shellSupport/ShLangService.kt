// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.shellSupport

import com.intellij.openapi.project.Project
import com.intellij.psi.tree.IElementType

interface ShLangService {
  val promptContentElementType: IElementType

  fun getShellCommandTokens(project: Project, command: String): List<String>?
}
