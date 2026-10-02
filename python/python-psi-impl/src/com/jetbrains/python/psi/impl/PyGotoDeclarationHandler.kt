// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandlerBase
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.resolve.FileContextUtil
import com.jetbrains.python.PyUserInitiatedResolvableReference
import com.jetbrains.python.psi.PyQualifiedExpression
import com.jetbrains.python.psi.PyReferenceOwner
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.resolve.PyResolveUtil
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.pyi.PyiUtil
import org.jetbrains.annotations.ApiStatus

/**
 * [com.intellij.codeInsight.navigation.actions.GotoDeclarationAction] uses [PsiElement.findReferenceAt].
 * This method knows nothing about execution context and [PyBaseElementImpl] injects loose [TypeEvalContext].
 * While regular methods are indexed, [com.jetbrains.python.codeInsight.PyCustomMember] are not.
 * As result, "go to declaration" failed to resolve reference pointing to another files which leads to bugs like PY-18089.
 *
 *
 * "Go to declaration" is always user-initiated action, so we resolve it manually using best context
 *
 * Other user actions, e.g. quick documentation, find usages, and rename, get the target element from
 * [com.jetbrains.python.codeInsight.PyTargetElementEvaluator]. That evaluator also uses a user-initiated context.
 *
 * @author Ilya.Kazakevich
 */
@ApiStatus.Internal
class PyGotoDeclarationHandler : GotoDeclarationHandlerBase() {
  override fun getGotoDeclarationTarget(sourceElement: PsiElement?, editor: Editor?): PsiElement? {
    return getGotoDeclarationTargets(sourceElement, -1, editor)?.firstOrNull()
  }

  override fun getGotoDeclarationTargets(sourceElement: PsiElement?, offset: Int, editor: Editor?): Array<out PsiElement>? {
    sourceElement ?: return null
    val context =
      PyResolveContext.defaultContext(TypeEvalContext.userInitiated(sourceElement.project, sourceElement.containingFile))

    val parent = sourceElement.parent
    val referenceOwner = sourceElement as? PyReferenceOwner ?: parent as? PyReferenceOwner
    if (referenceOwner != null) {
      val navigationSource = FileContextUtil.getContextFile(sourceElement) ?: sourceElement
      val results = PyResolveUtil.multiResolveDeclaration(referenceOwner.getReference(context), context)
        .map { PyiUtil.getNavigationTarget(it, navigationSource) }
        .filter { it !== referenceOwner }
        .groupBy { it.containingFile }
        .flatMap { (containingFile, declarations) ->
          if (containingFile != sourceElement.containingFile)
            declarations.take(1)
          // if it's a qualified expression, then it could be a union, so go to the different declarations
          //  otherwise go to the most recent assignment
          else if ((referenceOwner as? PyQualifiedExpression)?.isQualified == true)
            declarations
          else declarations.takeLast(1)
        }
      if (results.isNotEmpty()) {
        return results.toTypedArray()
      }
    }

    // If element is not ref owner, it still may have provided references, lets find some
    return sourceElement.findProvidedReferenceAndResolve() ?: parent?.findProvidedReferenceAndResolve()
  }
}

private fun PsiElement.findProvidedReferenceAndResolve(): Array<PsiElement>? =
  references
    .firstNotNullOfOrNull { ref ->
      (ref as? PyUserInitiatedResolvableReference)?.userInitiatedResolve()?.takeIf { it !== this }
    }
    ?.let { arrayOf(it) }
