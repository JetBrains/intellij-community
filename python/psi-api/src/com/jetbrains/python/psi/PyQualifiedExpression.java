/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a qualified expression, that is, of "a.b.c..." sort.
 * User: dcheryasov
 * Date: Oct 18, 2008
 */
public interface PyQualifiedExpression extends PyExpression {
  @Nullable
  PyExpression getQualifier();

  /**
   * Checks if the expression is qualified.
   *
   * Unlike {@link #getQualifier()}, it may not require AST access.
   */
  boolean isQualified();

  /**
   * Returns the qualified name for the expression if all the qualifiers are qualified expressions.
   */
  @Nullable
  QualifiedName asQualifiedName();

  /**
   * Returns the name to the right of the qualifier.
   *
   * @return the name referenced by the expression.
   */
  @Nullable
  String getReferencedName();

  /**
   * Returns the element representing the name (to the right of the qualifier).
   *
   * @return the name element.
   */
  @Nullable
  ASTNode getNameElement();

  @NotNull
  PsiPolyVariantReference getReference(PyResolveContext resolveContext);
}
