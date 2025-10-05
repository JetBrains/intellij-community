/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.psi.impl

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandlerBase
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.resolve.FileContextUtil
import com.jetbrains.python.PyUserInitiatedResolvableReference
import com.jetbrains.python.psi.PyElement
import com.jetbrains.python.psi.PyReferenceOwner
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.resolve.PyResolveUtil
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.pyi.PyiUtil

/**
 * [com.intellij.codeInsight.navigation.actions.GotoDeclarationAction] uses [PsiElement.findReferenceAt].
 * This method knows nothing about execution context and [PyBaseElementImpl] injects loose [TypeEvalContext].
 * While regular methods are indexed, [com.jetbrains.python.codeInsight.PyCustomMember] are not.
 * As result, "go to declaration" failed to resolve reference pointing to another files which leads to bugs like PY-18089.
 *
 *
 * "Go to declaration" is always user-initiated action, so we resolve it manually using best context
 *
 * @author Ilya.Kazakevich
 */
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
      val results = PyResolveUtil.multiResolveDeclaration(referenceOwner.getReference(context), context)
          .map {
            if (PyiUtil.isInsideStub(it) && !PyiUtil.isInsideStub(FileContextUtil.getContextFile(sourceElement)!!)) {
              PyiUtil.getOriginalElement(it as PyElement) ?: it
            }
            else it
          }
          .filter { it !== referenceOwner }
          .groupBy { it.containingFile }
          .flatMap { if (it.key == sourceElement.containingFile) it.value else listOf(it.value.first()) }
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