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

import com.intellij.psi.PsiElement;
import com.intellij.util.Processor;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyWithAncestors;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author vlan
 */
public interface PyClassLikeType extends PyCallableType, PyWithAncestors {
  boolean isDefinition();

  PyClassLikeType toInstance();

  @Nullable
  String getClassQName();

  @NotNull
  List<PyClassLikeType> getSuperClassTypes(@NotNull TypeEvalContext context);

  @Nullable
  List<? extends RatedResolveResult> resolveMember(@NotNull final String name, @Nullable PyExpression location,
                                                   @NotNull AccessDirection direction, @NotNull PyResolveContext resolveContext,
                                                   boolean inherited);

  // TODO: Pull to PyType at next iteration
  /**
   * Visits all class members. This method is better then bare class since it uses type info and supports not only classes but
   * class-like structures as well. Consider using user-friendly wrapper {@link PyTypeUtil#getMembersOfType(PyClassLikeType, Class, TypeEvalContext)}
   *
   * @param processor visitor
   * @param inherited call on parents too
   * @param context   context to be used to resolve types
   * @see PyTypeUtil#getMembersOfType(PyClassLikeType, Class, TypeEvalContext)
   */
  void visitMembers(@NotNull Processor<PsiElement> processor, boolean inherited, @NotNull TypeEvalContext context);

  boolean isValid();

  @Nullable
  PyClassLikeType getMetaClassType(@NotNull TypeEvalContext context, boolean inherited);
}
