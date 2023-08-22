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
package com.jetbrains.python.psi.impl;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandlerBase;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.util.ObjectUtils;
import com.jetbrains.python.PyUserInitiatedResolvableReference;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.PyResolveUtil;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.pyi.PyiFile;
import com.jetbrains.python.pyi.PyiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * {@link com.intellij.codeInsight.navigation.actions.GotoDeclarationAction} uses {@link PsiElement#findReferenceAt(int)}.
 * This method knows nothing about execution context and {@link PyBaseElementImpl} injects loose {@link TypeEvalContext}.
 * While regular methods are indexed, {@link com.jetbrains.python.codeInsight.PyCustomMember} are not.
 * As result, "go to declaration" failed to resolve reference pointing to another files which leads to bugs like PY-18089.
 * <p>
 * "Go to declaration" is always user-initiated action, so we resolve it manually using best context
 *
 * @author Ilya.Kazakevich
 */
public final class PyGotoDeclarationHandler extends GotoDeclarationHandlerBase {
  @Nullable
  @Override
  public PsiElement getGotoDeclarationTarget(@Nullable final PsiElement sourceElement, final Editor editor) {
    if (sourceElement == null) {
      return null;
    }
    final PyResolveContext context =
      PyResolveContext.defaultContext(TypeEvalContext.userInitiated(sourceElement.getProject(), sourceElement.getContainingFile()));

    PyReferenceOwner referenceOwner = null;
    final PsiElement parent = sourceElement.getParent();
    if (sourceElement instanceof PyReferenceOwner) {
      referenceOwner = (PyReferenceOwner)sourceElement;
    }
    else if (parent instanceof PyReferenceOwner) {
      referenceOwner = (PyReferenceOwner)parent; //Reference expression may be parent of IDENTIFIER
    }
    if (referenceOwner != null) {
      PsiElement resolved = PyResolveUtil.resolveDeclaration(referenceOwner.getReference(context), context);
      if (resolved != null && resolved.getContainingFile() instanceof PyiFile) {
        return ObjectUtils.chooseNotNull(PyiUtil.getOriginalElement((PyElement)resolved), resolved);
      }
      return resolved != referenceOwner ? resolved : null;
    }
    // If element is not ref owner, it still may have provided references, lets find some
    final PsiElement element = findProvidedReferenceAndResolve(sourceElement);
    if (element != null) {
      return element;
    }
    if (parent != null) {
      return findProvidedReferenceAndResolve(parent);
    }
    return null;
  }

  @Nullable
  private static PsiElement findProvidedReferenceAndResolve(@NotNull final PsiElement sourceElement) {
    for (final PsiReference reference : sourceElement.getReferences()) {
      if (reference instanceof PyUserInitiatedResolvableReference) {
        final PsiElement element = ((PyUserInitiatedResolvableReference)reference).userInitiatedResolve();
        if (element != null && element != sourceElement) {
          return element;
        }
      }
    }
    return null;
  }
}
