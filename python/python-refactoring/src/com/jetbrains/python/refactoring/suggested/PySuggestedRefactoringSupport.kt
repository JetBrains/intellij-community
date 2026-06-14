// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.refactoring.suggested

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.util.hasErrorElementInRange
import com.intellij.psi.util.startOffset
import com.intellij.refactoring.suggested.SuggestedRefactoringAvailability
import com.intellij.refactoring.suggested.SuggestedRefactoringExecution
import com.intellij.refactoring.suggested.SuggestedRefactoringState
import com.intellij.refactoring.suggested.SuggestedRefactoringStateChanges
import com.intellij.refactoring.suggested.SuggestedRefactoringSupport
import com.intellij.refactoring.suggested.SuggestedRefactoringUI
import com.jetbrains.python.PyNames
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.psi.PyElement
import com.jetbrains.python.psi.PyFromImportStatement
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyImportElement
import com.jetbrains.python.psi.PyParameter
import com.jetbrains.python.psi.PyParameterList
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.pyi.PyiUtil

class PySuggestedRefactoringSupport : SuggestedRefactoringSupport {

  object Helper {
    internal fun isAvailableForChangeSignature(element: PsiElement): Boolean {
      if (element !is PyFunction) return false
      val name = element.name
      return name != null && PyNames.isIdentifier(name) && element.property == null
    }

    internal fun defaultValue(parameter: SuggestedRefactoringSupport.Parameter): String? {
      return (parameter.additionalData as? ParameterData)?.defaultValue
    }

    internal fun isAvailableForRename(element: PsiElement): Boolean {
      if (element !is PsiNameIdentifierOwner) return false
      val name = element.name
      if (name == null || !PyNames.isIdentifier(name)) return false
      if (element !is PyParameter) return true
      val function = containingFunction(element)
      return function != null && !isAvailableForChangeSignature(function)
    }

    /**
     * The import binds a single name (a plain `import foo` or any `from m import name`), so adding/editing
     * its `as` alias changes that visible name. Dotted `import a.b.c` is excluded: replacing the whole
     * `a.b.c` qualifier on usages is out of scope for the automatic suggestion. A `from __future__` import
     * is excluded because its names are fixed.
     */
    internal fun isAvailableForImportAlias(element: PsiElement): Boolean {
      return element is PyImportElement &&
             element.importReferenceExpression != null &&
             element.importedQName.let { it != null && it.componentCount == 1 } &&
             (element.parent as? PyFromImportStatement)?.isFromFuture != true
    }

    internal fun shouldSuppressRefactoringForDeclaration(state: SuggestedRefactoringState): Boolean {
      val element = state.restoredDeclarationCopy()
      val declaration = state.declaration
      return element == null ||
             PyiUtil.isOverload(element, TypeEvalContext.codeAnalysis(element.project, element.containingFile)) ||
             declaration != null && PyiUtil.isInsideStub(declaration) ||
             declaration is PyElement && PyiUtil.getPythonStub(declaration) != null
    }

    private fun containingFunction(parameter: PyParameter): PyFunction? {
      return (parameter.parent as? PyParameterList)?.containingFunction
    }
  }

  override fun isAnchor(psiElement: PsiElement): Boolean = findSupport(psiElement) != null

  override fun signatureRange(anchor: PsiElement): TextRange? = findSupport(anchor)?.signatureRange(anchor)?.takeIf {
    !anchor.containingFile.hasErrorElementInRange(it)
  }

  override fun importsRange(psiFile: PsiFile): TextRange? = null

  override fun nameRange(anchor: PsiElement): TextRange? {
    if (anchor is PyImportElement) {
      val alias = anchor.asNameElement
      if (alias != null) return alias.nameIdentifier?.textRange ?: alias.textRange
      return anchor.importReferenceExpression?.textRange
    }
    return (anchor as? PsiNameIdentifierOwner)?.nameIdentifier?.textRange
  }

  override fun isIdentifierStart(c: Char): Boolean = c == '_' || Character.isLetter(c)

  override fun isIdentifierPart(c: Char): Boolean = isIdentifierStart(c) || Character.isDigit(c)

  override val stateChanges: SuggestedRefactoringStateChanges = PySuggestedRefactoringStateChanges(this)

  override val availability: SuggestedRefactoringAvailability = PySuggestedRefactoringAvailability(this)

  override val ui: SuggestedRefactoringUI = PySuggestedRefactoringUI

  override val execution: SuggestedRefactoringExecution = PySuggestedRefactoringExecution(this)

  internal data class ParameterData(val defaultValue: String?) : SuggestedRefactoringSupport.ParameterAdditionalData

  private fun findSupport(declaration: PsiElement): SupportInternal? {
    return sequenceOf(ChangeSignatureSupport, RenameSupport(this), ImportAliasSupport).firstOrNull { it.isApplicable(declaration) }
  }

  private interface SupportInternal {

    fun isApplicable(element: PsiElement): Boolean
    fun signatureRange(declaration: PsiElement): TextRange?
  }

  private object ChangeSignatureSupport : SupportInternal {

    override fun isApplicable(element: PsiElement): Boolean = Helper.isAvailableForChangeSignature(element)

    override fun signatureRange(declaration: PsiElement): TextRange? {
      declaration as PyFunction
      val name = declaration.nameIdentifier ?: return null
      val colon = declaration.node.findChildByType(PyTokenTypes.COLON) ?: return null
      return TextRange.create(name.startOffset, colon.startOffset)
    }
  }

  private class RenameSupport(private val mainSupport: PySuggestedRefactoringSupport) : SupportInternal {

    override fun isApplicable(element: PsiElement): Boolean = Helper.isAvailableForRename(element)
    override fun signatureRange(declaration: PsiElement): TextRange? = mainSupport.nameRange(declaration)
  }

  private object ImportAliasSupport : SupportInternal {

    override fun isApplicable(element: PsiElement): Boolean = Helper.isAvailableForImportAlias(element)

    // Watch the whole import element so that typing " as alias" right after the imported name is tracked.
    override fun signatureRange(declaration: PsiElement): TextRange? = declaration.textRange
  }
}
