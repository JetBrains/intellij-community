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

import com.intellij.psi.PsiElement;
import com.intellij.psi.StubBasedPsiElement;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.stubs.PyImportElementStub;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author yole
 */
public interface PyImportElement extends PyElement, PyImportedNameDefiner, StubBasedPsiElement<PyImportElementStub> {
  @Nullable
  PyReferenceExpression getImportReferenceExpression();

  @Nullable
  QualifiedName getImportedQName();

  @Nullable
  PyTargetExpression getAsNameElement();

  @Nullable
  String getAsName();

  /**
   * @return name under which the element is visible, that is, "as name" is there is one, or just name.
   */
  @Nullable
  String getVisibleName();

  PyStatement getContainingImportStatement();
  
  @Nullable
  PsiElement getElementNamed(String name, boolean resolveImportElement);

  /**
   * @deprecated Use {@link #multiResolve()} instead.
   */
  @Deprecated
  @Nullable
  PsiElement resolve();

  /**
   * Resolves the import element to the elements being imported.
   */
  @NotNull
  List<RatedResolveResult> multiResolve();
}
