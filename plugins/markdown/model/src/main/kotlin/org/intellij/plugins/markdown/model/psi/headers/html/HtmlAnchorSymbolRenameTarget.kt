package org.intellij.plugins.markdown.model.psi.headers.html

import com.intellij.model.Pointer
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.refactoring.rename.api.RenameTarget

internal class HtmlAnchorSymbolRenameTarget(val symbol: HtmlAnchorSymbol): RenameTarget {
  override fun createPointer(): Pointer<out RenameTarget> {
    return Pointer.hardPointer(this)
    //val pointer = symbol.createPointer()
    //return Pointer.delegatingPointer(pointer) { HtmlAnchorSymbolRenameTarget(it) }
  }

  override val targetName: String
    get() = symbol.anchorText

  override fun presentation(): TargetPresentation {
    return symbol.presentation()
  }
}
