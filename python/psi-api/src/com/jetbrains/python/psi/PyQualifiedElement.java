// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.util.QualifiedName;
import org.jetbrains.annotations.Nullable;

public interface PyQualifiedElement extends PyReferenceOwner {

  @Nullable
  PyExpression getQualifier();

  /**
   * Checks if the element is qualified.
   *
   * Unlike {@link #getQualifier()}, it may not require AST access.
   */
  boolean isQualified();

  /**
   * Returns the qualified name for the element.
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
   * @return the name referenced by the element.
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
