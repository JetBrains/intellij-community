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
package com.jetbrains.python.psi.resolve;

import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.PyExpression;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class QualifiedRatedResolveResult extends RatedResolveResult implements QualifiedResolveResult {

  @NotNull
  private final List<PyExpression> myQualifiers;
  private final boolean myIsImplicit;

  public QualifiedRatedResolveResult(@NotNull PsiElement element, @NotNull List<PyExpression> qualifiers, int rate, boolean isImplicit) {
    super(rate, element);
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
