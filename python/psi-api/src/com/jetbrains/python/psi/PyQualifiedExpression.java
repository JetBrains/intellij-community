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

import com.intellij.lang.ASTNode;
import com.intellij.psi.util.QualifiedName;
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
   * Returns the qualified name for the expression.
   * <p>
   * If it has no qualifier, the result is the same as {@code QualifiedName.fromDottedString(getReferencedName())}.
   * Otherwise, the qualified name is built of {@code getReferencedName()} followed (to the left) by the components
   * of the qualifier <em>if it consists only of reference expressions</em>. In any other case, the result is null.
   * <p>
   * Note that it means that for pseudo-qualified operations that map to "magic" methods, the trailing component of
   * the result is the corresponding "dunder" name: {@code __add__}, {@code __neg__}, etc.
   * <p>
   * Examples:
   * <ul>
   * <li>{@code foo -> foo}</li>
   * <li>{@code foo.bar -> foo.bar}</li>
   * <li>{@code foo[0] -> foo.__getitem__}</li>
   * <li>{@code foo[0].bar -> null}</li>
   * <li>{@code foo[0][1] -> null}</li>
   * <li>{@code foo().bar -> null}</li>
   * <li>{@code -foo -> foo.__neg__}</li>
   * <li>{@code foo + bar -> foo.__add__}</li>
   * <li>{@code foo + bar + baz -> null}</li>
   * </ul>
   *
   * @see #getReferencedName()
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
}
