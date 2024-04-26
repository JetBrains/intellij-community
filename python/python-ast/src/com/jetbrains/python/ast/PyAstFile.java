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
package com.jetbrains.python.ast;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.jetbrains.python.ast.controlFlow.AstScopeOwner;
import com.jetbrains.python.psi.FutureFeature;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@ApiStatus.Experimental
public interface PyAstFile extends PyAstElement, PsiFile, PyAstDocStringOwner, AstScopeOwner {
  default List<? extends PyAstStatement> getStatements() {
    List<PyAstStatement> stmts = new ArrayList<>();
    for (PsiElement child : getChildren()) {
      if (child instanceof PyAstStatement statement) {
        stmts.add(statement);
      }
    }
    return stmts;
  }

  List<? extends PyAstTargetExpression> getTopLevelAttributes();

  @Nullable
  default PyAstTargetExpression findTopLevelAttribute(@NotNull String name) {
    return findByName(name, getTopLevelAttributes());
  }

  LanguageLevel getLanguageLevel();

  /**
   * Return true if the file contains a 'from __future__ import ...' statement with given feature.
   */
  boolean hasImportFromFuture(FutureFeature feature);

  private static <T extends PsiNamedElement> T findByName(@NotNull String name, @NotNull List<T> namedElements) {
    for (T namedElement : namedElements) {
      if (name.equals(namedElement.getName())) {
        return namedElement;
      }
    }
    return null;
  }

  @ApiStatus.Internal
  default boolean isAcceptedFor(@NotNull Class<?> visitorClass) {
    return true;
  }
}
