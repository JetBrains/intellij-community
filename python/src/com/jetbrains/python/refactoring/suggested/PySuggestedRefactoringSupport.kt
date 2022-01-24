// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.refactoring.suggested

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.util.hasErrorElementInRange
import com.intellij.refactoring.suggested.*
import com.jetbrains.python.PyNames
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.psi.PyElement
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyParameter
import com.jetbrains.python.psi.PyParameterList
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.pyi.PyiUtil

class PySuggestedRefactoringSupport : SuggestedRefactoringSupport {

  companion object {
    internal fun isAvailableForChangeSignature(element: PsiElement): Boolean {
      return element is PyFunction &&
             element.name.let { it != null && PyNames.isIdentifier(it) } &&
             element.property == null &&
             !shouldBeSuppressed(element)
    }

    internal fun defaultValue(parameter: SuggestedRefactoringSupport.Parameter): String? {
      return (parameter.additionalData as? ParameterData)?.defaultValue
    }

    internal fun isAvailableForRename(element: PsiElement): Boolean {
      return element is PsiNameIdentifierOwner &&
             element.name.let { it != null && PyNames.isIdentifier(it) } &&
             (element !is PyParameter || containingFunction(element).let { it != null && !isAvailableForChangeSignature(it) }) &&
             !shouldBeSuppressed(element)
    }

    internal fun shouldSuppressRefactoringForDeclaration(state: SuggestedRefactoringState): Boolean {
      // don't merge with `shouldBeSuppressed` because `shouldBeSuppressed` could be invoked in EDT and resolve below could slow down it
      val element = state.restoredDeclarationCopy()
      return PyiUtil.isOverload(element, TypeEvalContext.codeAnalysis(element.project, element.containingFile))
    }

    private fun shouldBeSuppressed(element: PsiElement): Boolean {
      if (PyiUtil.isInsideStub(element)) return true
      if (element is PyElement && PyiUtil.getPythonStub(element) != null) return true

      return false
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

  override fun nameRange(anchor: PsiElement): TextRange? = (anchor as? PsiNameIdentifierOwner)?.nameIdentifier?.textRange

  override fun isIdentifierStart(c: Char): Boolean = c == '_' || Character.isLetter(c)

  override fun isIdentifierPart(c: Char): Boolean = isIdentifierStart(c) || Character.isDigit(c)

  override val stateChanges: SuggestedRefactoringStateChanges = PySuggestedRefactoringStateChanges(this)

  override val availability: SuggestedRefactoringAvailability = PySuggestedRefactoringAvailability(this)

  override val ui: SuggestedRefactoringUI = PySuggestedRefactoringUI

  override val execution: SuggestedRefactoringExecution = PySuggestedRefactoringExecution(this)

  internal data class ParameterData(val defaultValue: String?) : SuggestedRefactoringSupport.ParameterAdditionalData

  private fun findSupport(declaration: PsiElement): SupportInternal? {
    return sequenceOf(ChangeSignatureSupport, RenameSupport(this)).firstOrNull { it.isApplicable(declaration) }
  }

  private interface SupportInternal {

    fun isApplicable(element: PsiElement): Boolean
    fun signatureRange(declaration: PsiElement): TextRange?
  }

  private object ChangeSignatureSupport : SupportInternal {

    override fun isApplicable(element: PsiElement): Boolean = isAvailableForChangeSignature(element)

    override fun signatureRange(declaration: PsiElement): TextRange? {
      declaration as PyFunction
      val name = declaration.nameIdentifier ?: return null
      val colon = declaration.node.findChildByType(PyTokenTypes.COLON) ?: return null
      return TextRange.create(name.startOffset, colon.startOffset)
    }
  }

  private class RenameSupport(private val mainSupport: PySuggestedRefactoringSupport) : SupportInternal {

    override fun isApplicable(element: PsiElement): Boolean = isAvailableForRename(element)
    override fun signatureRange(declaration: PsiElement): TextRange? = mainSupport.nameRange(declaration)
  }
}