/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python.psi.resolve;

import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveResult;
import com.jetbrains.python.psi.PyExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public interface QualifiedResolveResult extends ResolveResult {

  @NotNull
  QualifiedResolveResult EMPTY = new QualifiedResolveResult() {

    @NotNull
    @Override
    public List<PyExpression> getQualifiers() {
      return Collections.emptyList();
    }

    @Override
    public boolean isImplicit() {
      return false;
    }

    @Nullable
    @Override
    public PsiElement getElement() {
      return null;
    }

    @Override
    public boolean isValidResult() {
      return false;
    }
  };

  /**
   * @return the qualifiers which were collected while following assignments chain.
   *
   * @see com.jetbrains.python.psi.PyReferenceExpression#followAssignmentsChain(PyResolveContext)
   * @see com.jetbrains.python.psi.PyReferenceExpression#multiFollowAssignmentsChain(PyResolveContext)
   */
  @NotNull
  List<PyExpression> getQualifiers();

  /**
   * @return true iff the resolve result is implicit, that is, not exact but by divination and looks reasonable. 
   */
  boolean isImplicit();

  @NotNull
  static QualifiedResolveResult create(@NotNull PsiElement element, @NotNull List<PyExpression> qualifiers, boolean isImplicit) {
    class QualifiedResolveResultImpl extends RatedResolveResult implements QualifiedResolveResult {

      @NotNull
      private final List<PyExpression> myQualifiers;
      private final boolean myIsImplicit;

      private QualifiedResolveResultImpl(@NotNull PsiElement element, @NotNull List<PyExpression> qualifiers, boolean isImplicit) {
        super(isImplicit ? RATE_LOW : RATE_NORMAL, element);
        myQualifiers = qualifiers;
        myIsImplicit = isImplicit;
      }

      @Override
      @NotNull
      public List<PyExpression> getQualifiers() {
        return myQualifiers;
      }

      @Override
      public boolean isImplicit() {
        return myIsImplicit;
      }
    }

    return new QualifiedResolveResultImpl(element, qualifiers, isImplicit);
  }
}
