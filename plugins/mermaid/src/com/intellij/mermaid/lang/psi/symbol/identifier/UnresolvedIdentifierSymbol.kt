// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.lang.psi.symbol.identifier

import com.intellij.find.usages.api.UsageHandler
import com.intellij.mermaid.lang.psi.MermaidActorStatement
import com.intellij.mermaid.lang.psi.MermaidBranchStatement
import com.intellij.mermaid.lang.psi.MermaidClassStatement
import com.intellij.mermaid.lang.psi.MermaidElementDef
import com.intellij.mermaid.lang.psi.MermaidEntityDeclaration
import com.intellij.mermaid.lang.psi.MermaidNamedPsiElement
import com.intellij.mermaid.lang.psi.MermaidRequirementDef
import com.intellij.mermaid.lang.psi.MermaidStateDeclaration
import com.intellij.mermaid.lang.psi.MermaidSubgraphName
import com.intellij.model.Pointer
import com.intellij.navigation.SymbolNavigationService
import com.intellij.openapi.util.TextRange
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiFileRange
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.util.PsiTreeUtil.instanceOf
import com.intellij.refactoring.rename.api.RenameTarget
import com.intellij.refactoring.suggested.startOffset

// TODO: Split UnresolvedIdentifierSymbol and IdentifierSymbol
@Suppress("UnstableApiUsage")
open class UnresolvedIdentifierSymbol(
  override val file: PsiFile,
  override val range: TextRange,
  override val text: String
): MermaidIdentifierSymbol, RenameTarget {
  override val maximalSearchScope: SearchScope
    get() = LocalSearchScope(file, file.name)

  override val targetName: String
    get() = text

  // No return type is specified, so that we can compile against both pre-231.8770 and 231.8770
  override fun presentation(): TargetPresentation {
    return SymbolNavigationService.getInstance().presentationBuilder(text).presentation()
  }

  override fun createPointer(): Pointer<out UnresolvedIdentifierSymbol> {
    val project = file.project
    val base = SmartPointerManager.getInstance(project).createSmartPsiFileRangePointer(file, range)
    return UnresolvedIdentifierSymbolPointer(base, text)
  }

  private class UnresolvedIdentifierSymbolPointer(
    private val base: SmartPsiFileRange,
    private val text: String
  ) : Pointer<UnresolvedIdentifierSymbol> {
    override fun dereference(): UnresolvedIdentifierSymbol? {
      val file = base.containingFile ?: return null
      val range = base.range ?: return null
      return UnresolvedIdentifierSymbol(file, TextRange.create(range), text)
    }
  }

  override val usageHandler: UsageHandler
    get() = UsageHandler.createEmptyUsageHandler(text)

  override val searchText: String
    get() = text

  companion object {
    fun createPointer(element: MermaidNamedPsiElement): Pointer<UnresolvedIdentifierSymbol> {
      val (base, textInElement) = getPointerArgs(element)
      return UnresolvedIdentifierSymbolPointer(base, textInElement)
    }

    fun getPointerArgs(element: MermaidNamedPsiElement): Pair<SmartPsiFileRange, String> {
      val text = element.text
      val rangeInElement = TextRange(0, text.length)
      val absoluteRange = rangeInElement.shiftRight(element.startOffset)
      val textInElement = rangeInElement.substring(text)
      val file = element.containingFile
      val project = file.project
      val base = SmartPointerManager.getInstance(project).createSmartPsiFileRangePointer(file, absoluteRange)
      return Pair(base, textInElement)
    }

    val MermaidNamedPsiElement.isDeclaration: Boolean
      get() = instanceOf(
        parent,
        *listOf(
          MermaidSubgraphName::class,
          MermaidActorStatement::class,
          MermaidClassStatement::class,
          MermaidStateDeclaration::class,
          MermaidEntityDeclaration::class,
          MermaidRequirementDef::class,
          MermaidElementDef::class,
          MermaidBranchStatement::class
        ).map { it.java }.toTypedArray()
      )
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as UnresolvedIdentifierSymbol

    if (file != other.file) return false
    if (text != other.text) return false

    return true
  }

  override fun hashCode(): Int {
    var result = file.hashCode()
    result = 31 * result + text.hashCode()
    return result
  }
}
