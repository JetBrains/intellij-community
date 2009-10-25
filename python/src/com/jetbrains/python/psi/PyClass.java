/*
 *  Copyright 2005 Pythonid Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS"; BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.jetbrains.python.psi;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.StubBasedPsiElement;
import com.jetbrains.python.psi.stubs.PyClassStub;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a class declaration in source.
 */
public interface PyClass extends PsiNamedElement, PyStatement, NameDefiner, PyDocStringOwner, StubBasedPsiElement<PyClassStub> {
  @NotNull
  PyStatementList getStatementList();

  @NotNull
  PyExpression[] getSuperClassExpressions();

  @Nullable
  PsiElement[] getSuperClassElements();       

  @NotNull
  PyClass[] getSuperClasses();

  @NotNull
  PyFunction[] getMethods();

  @Nullable
  PyFunction findMethodByName(@NotNull @NonNls final String name);

  PyTargetExpression[] getClassAttributes();

  PyTargetExpression[] getInstanceAttributes();

  /**
   * @return true if the class is new-style and descends from 'object'.
   */
  boolean isNewStyleClass();

  /**
   * A lazy way to list ancestor classes width first, in method-resolution order (MRO).
   * @return an iterable of ancestor classes.
   */
  Iterable<PyClass> iterateAncestors();

  /**
   * @param target
   * @param parent
   * @return True iff this and parent are the same or parent is one of our superclasses.
   */
  boolean isSublclass(PyClass parent);
}
