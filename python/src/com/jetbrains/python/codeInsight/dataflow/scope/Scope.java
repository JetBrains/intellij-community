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
package com.jetbrains.python.codeInsight.dataflow.scope;

import com.intellij.codeInsight.dataflow.DFALimitExceededException;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.jetbrains.python.psi.PyImportedNameDefiner;
import com.jetbrains.python.psi.PyTargetExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * @author oleg
 */
public interface Scope {
  /*
   * @return defined scope local/instance/class variables and parameters, using reaching defs
   */
  @Nullable
  ScopeVariable getDeclaredVariable(@NotNull PsiElement anchorElement, @NotNull String name) throws DFALimitExceededException;

  boolean isGlobal(String name);

  boolean isNonlocal(String name);

  boolean containsDeclaration(String name);

  @NotNull
  List<PyImportedNameDefiner> getImportedNameDefiners();

  @NotNull
  Collection<PsiNamedElement> getNamedElements(String name, boolean includeNestedGlobals);

  @NotNull
  Collection<PsiNamedElement> getNamedElements();

  @NotNull
  Collection<PyTargetExpression> getTargetExpressions();
}
