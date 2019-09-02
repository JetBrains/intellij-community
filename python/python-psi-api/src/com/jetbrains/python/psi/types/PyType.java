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
package com.jetbrains.python.psi.types;

import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyQualifiedNameOwner;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * Represents a type of an expression.
 *
 * @author yole
 */
public interface PyType {

  /**
   * Returns the declaration element that can be used to refer to this type inside type hints. Normally, it's a symbol
   * that can be imported to mentioned the type in type annotations and comments anywhere else.
   * <p>
   * Typical examples are target expressions in LHS of assignments in {@code TypeVar} and named tuple definitions, as well as
   * class definitions themselves for plain class and generic types.
   */
  @Nullable
  default PyQualifiedNameOwner getDeclarationElement() {
    return null;
  }

  /**
   * Resolves an attribute of type.
   *
   * @param name      attribute name
   * @param location  the expression of type qualifierType on which the member is being resolved (optional)
   * @param direction
   * @param resolveContext
   * @return null if name definitely cannot be found (e.g. in a qualified reference),
   *         or an empty list if name is not found but other contexts are worth looking at,
   *         or a list of elements that define the name, a la multiResolve().
   */
  @Nullable
  List<? extends RatedResolveResult> resolveMember(@NotNull String name, @Nullable final PyExpression location,
                                                   @NotNull final AccessDirection direction, @NotNull final PyResolveContext resolveContext);

  /**
   * Proposes completion variants from type's attributes.
   *
   *
   * @param location   the reference on which the completion was invoked
   * @param context    to share state between nested invocations
   * @return completion variants good for {@link com.intellij.psi.PsiReference#getVariants} return value.
   */
  Object[] getCompletionVariants(String completionPrefix, PsiElement location, ProcessingContext context);

  /**
   * Context key for access to a set of names already found by variant search.
   */
  Key<Set<String>> CTX_NAMES = new Key<>("Completion variants names");

  /**
   * TODO rename it to something like getPresentableName(), because it's not clear that these names are actually visible to end-user
   * @return name of the type
   */
  @Nullable
  String getName();

  /**
   * @return true if the type is a known built-in type.
   */
  boolean isBuiltin();

  void assertValid(String message);
}
