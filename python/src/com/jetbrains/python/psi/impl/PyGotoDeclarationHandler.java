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
import com.jetbrains.python.psi.PyReferenceOwner;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.Nullable;

/**
 * {@link com.intellij.codeInsight.navigation.actions.GotoDeclarationAction} uses {@link PsiElement#findReferenceAt(int)}.
 * This method knows nothing about execution context and {@link PyBaseElementImpl} injects loose {@link TypeEvalContext}.
 * While regular methods are indexed, {@link com.jetbrains.python.codeInsight.PyCustomMember} are not.
 * As result, "go to declaration" failed to resolve reference pointing to another files which leads to bugs like PY-18089.
 * <p>
 * "Go to declaration" is always user-initiated action, so we resolve it manually using best conext
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
    PyReferenceOwner referenceOwner = null;
    if (sourceElement instanceof PyReferenceOwner) {
      referenceOwner = (PyReferenceOwner)sourceElement;
    }
    else if (sourceElement.getParent() instanceof PyReferenceOwner) {
      referenceOwner = (PyReferenceOwner)sourceElement.getParent(); //Reference expression may be parent of IDENTIFIER
    }
    if (referenceOwner == null) {
      return null;
    }

    final PyResolveContext context = PyResolveContext.noImplicits()
      .withTypeEvalContext(TypeEvalContext.userInitiated(sourceElement.getProject(), sourceElement.getContainingFile()));
    final PsiElement psiElement = referenceOwner.getReference(context).resolve();
    if (psiElement == null) {
      return null;
    }
    return psiElement;
  }
}
