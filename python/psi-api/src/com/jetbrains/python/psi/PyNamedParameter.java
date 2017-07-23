/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.StubBasedPsiElement;
import com.jetbrains.python.psi.stubs.PyNamedParameterStub;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a named parameter, as opposed to a tuple parameter.
 */
public interface PyNamedParameter extends PyParameter, PsiNamedElement, PsiNameIdentifierOwner, PyExpression, PyTypeCommentOwner,
                                          PyAnnotationOwner, StubBasedPsiElement<PyNamedParameterStub> {
  boolean isPositionalContainer();

  boolean isKeywordContainer();

  /**
   * Parameter is considered "keyword-only" if it appears after named or unnamed positional vararg parameter.
   * See PEP-3102 for more details.
   *
   * @return whether this parameter is keyword-only
   */
  boolean isKeywordOnly();

  /**
   * @param includeDefaultValue if true, include the default value after an "=".
   * @return canonical representation of parameter.
   * Includes asterisks for *param and **param, and name.
   */
  @NotNull
  default String getRepr(boolean includeDefaultValue) {
    return getRepr(includeDefaultValue, null);
  }

  /**
   * @param includeDefaultValue if true, include the default value after an "=".
   * @param context             context to be used to resolve argument type
   * @return canonical representation of parameter.
   * Includes asterisks for *param and **param, and name.
   * Also includes argument type if {@code context} is not null and resolved type is not unknown.
   */
  @NotNull
  String getRepr(boolean includeDefaultValue, @Nullable TypeEvalContext context);

  /**
   * @param context context to be used to resolve argument type
   * @return argument type. Returns element type for *param and value type for **param.
   */
  @Nullable
  PyType getArgumentType(@NotNull TypeEvalContext context);
}

