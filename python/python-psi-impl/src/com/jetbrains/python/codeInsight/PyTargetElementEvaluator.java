/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.codeInsight;

import com.intellij.codeInsight.TargetElementEvaluator;
import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyReferenceOwner;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.impl.references.PyReferenceBase;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.PyResolveUtil;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * Finds the target element for user actions that get it from {@link com.intellij.codeInsight.TargetElementUtil},
 * e.g. quick documentation, find usages, and rename.
 * These actions accept the element name, so this evaluator resolves the reference with a user-initiated context.
 * <p>
 * Go to declaration gets its targets from {@link com.jetbrains.python.psi.impl.PyGotoDeclarationHandler} first.
 * That handler also uses a user-initiated context.
 */
public final class PyTargetElementEvaluator implements TargetElementEvaluator {
  @Override
  public boolean includeSelfInGotoImplementation(@NotNull PsiElement element) {
    return false;
  }

  @Override
  public @Nullable PsiElement getElementByReference(@NotNull PsiReference ref, int flags) {
    if ((flags & TargetElementUtilBase.ELEMENT_NAME_ACCEPTED) == 0) {
      return null;
    }

    final PsiElement element = ref.getElement();
    // PsiFile.findReferenceAt gives a reference with a code analysis context, so get the reference again.
    // A provided reference, e.g. a BaseReference, does not belong to the element, so keep it.
    final var resolveContext =
      PyResolveContext.defaultContext(TypeEvalContext.userInitiated(element.getProject(), element.getContainingFile()));
    final PsiReference userInitiatedRef =
      ref instanceof PyReferenceBase && element instanceof PyReferenceOwner owner ? owner.getReference(resolveContext) : ref;

    PsiElement result = PyResolveUtil.resolveDeclaration(userInitiatedRef, resolveContext);
    Set<PsiElement> visited = new HashSet<>();
    visited.add(result);
    while (result instanceof PyReferenceExpression || result instanceof PyTargetExpression) {
      PsiElement nextResult = PyResolveUtil.resolveDeclaration(((PyReferenceOwner)result).getReference(resolveContext), resolveContext);
      if (nextResult != null && !visited.contains(nextResult) &&
          PsiTreeUtil.getParentOfType(element, ScopeOwner.class) == PsiTreeUtil.getParentOfType(result, ScopeOwner.class) &&
          (nextResult instanceof PyReferenceExpression || nextResult instanceof PyTargetExpression || nextResult instanceof PyParameter)) {
        visited.add(nextResult);
        result = nextResult;
      }
      else {
        break;
      }
    }
    return result;
  }
}
