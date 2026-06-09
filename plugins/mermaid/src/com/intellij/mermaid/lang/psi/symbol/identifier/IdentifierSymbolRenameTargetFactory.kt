// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.lang.psi.symbol.identifier

import com.intellij.model.Symbol
import com.intellij.openapi.project.Project
import com.intellij.refactoring.rename.api.RenameTarget
import com.intellij.refactoring.rename.symbol.SymbolRenameTargetFactory

@Suppress("UnstableApiUsage")
internal class IdentifierSymbolRenameTargetFactory: SymbolRenameTargetFactory {
  override fun renameTarget(project: Project, symbol: Symbol): RenameTarget? {
    return when (symbol) {
      is MermaidIdentifierSymbol -> IdentifierRenameTarget(symbol)
      else -> null
    }
  }
}
