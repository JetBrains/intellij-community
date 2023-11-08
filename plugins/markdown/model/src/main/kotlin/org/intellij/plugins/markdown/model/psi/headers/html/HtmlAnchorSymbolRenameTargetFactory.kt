package org.intellij.plugins.markdown.model.psi.headers.html

import com.intellij.model.Symbol
import com.intellij.openapi.project.Project
import com.intellij.refactoring.rename.api.RenameTarget
import com.intellij.refactoring.rename.symbol.SymbolRenameTargetFactory

internal class HtmlAnchorSymbolRenameTargetFactory: SymbolRenameTargetFactory {
  override fun renameTarget(project: Project, symbol: Symbol): RenameTarget? {
    return when (symbol) {
      is HtmlAnchorSymbol -> HtmlAnchorSymbolRenameTarget(symbol)
      else -> null
    }
  }
}
