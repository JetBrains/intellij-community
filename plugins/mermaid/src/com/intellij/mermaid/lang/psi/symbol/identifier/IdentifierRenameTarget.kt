// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.lang.psi.symbol.identifier

import com.intellij.model.Pointer
import com.intellij.psi.search.SearchScope
import com.intellij.refactoring.rename.api.RenameTarget

@Suppress("UnstableApiUsage")
internal class IdentifierRenameTarget(val symbol: MermaidIdentifierSymbol): RenameTarget {
  override val targetName: String
    get() = symbol.text

  override fun createPointer(): Pointer<out RenameTarget> {
    // TODO: Replace with delegating pointer to the symbol after dereference is fixed in the platform?
    // Create a plain hard reference pointer to prevent empty ranges being produced after pointer dereference
    return Pointer.hardPointer(this)
    // return Pointer.delegatingPointer(symbol.createPointer()) { IdentifierRenameTarget(it) }
  }

  override val maximalSearchScope: SearchScope?
    get() = symbol.maximalSearchScope

  override fun presentation() = symbol.presentation()
}
