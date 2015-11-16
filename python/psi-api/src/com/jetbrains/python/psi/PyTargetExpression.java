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
package com.jetbrains.python.psi;

import com.intellij.psi.*;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.stubs.PyTargetExpressionStub;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface PyTargetExpression extends PyQualifiedExpression, PsiNamedElement, PsiNameIdentifierOwner, PyDocStringOwner,
                                            PyQualifiedNameOwner, PyReferenceOwner, StubBasedPsiElement<PyTargetExpressionStub>,
                                            PyPossibleClassMember {
  PyTargetExpression[] EMPTY_ARRAY = new PyTargetExpression[0];

  /**
   * Find the value that maps to this target expression in an enclosing assignment expression.
   * Does not work with other expressions (e.g. if the target is in a 'for' loop).
   *
   * Operates at the AST level.
   *
   * @return the expression assigned to target via an enclosing assignment expression, or null.
   */
  @Nullable
  PyExpression findAssignedValue();

  /**
   * Resolves the value that maps to this target expression in an enclosing assignment expression.
   *
   * This method does not access AST if underlying PSI is stub based and the context doesn't allow switching to AST.
   */
  @Nullable
  PsiElement resolveAssignedValue(@NotNull PyResolveContext resolveContext);

  /**
   * Returns the qualified name (if there is any) assigned to the expression.
   *
   * This method does not access AST if underlying PSI is stub based.
   */
  @Nullable
  QualifiedName getAssignedQName();

  /**
   * If the value assigned to the target expression is a call, returns the (unqualified and unresolved) name of the
   * callee. Otherwise, returns null.
   *
   * @return the name of the callee or null if the assigned value is not a call.
   */
  @Nullable
  QualifiedName getCalleeName();

  @NotNull
  PsiReference getReference();
}
