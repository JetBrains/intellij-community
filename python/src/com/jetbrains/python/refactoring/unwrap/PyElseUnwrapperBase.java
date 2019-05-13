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
package com.jetbrains.python.refactoring.unwrap;

import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyElsePart;
import com.jetbrains.python.psi.PyIfStatement;
import org.jetbrains.annotations.NotNull;

/**
 * User : ktisha
 */
public abstract class PyElseUnwrapperBase extends PyUnwrapper {
  public PyElseUnwrapperBase(String description) {
    super(description);
  }

  @Override
  public boolean isApplicableTo(@NotNull PsiElement e) {
      return (e instanceof PyElsePart);
  }
  @Override
    protected void doUnwrap(PsiElement element, Context context) throws IncorrectOperationException {
      PyElement elseBranch;

      if (element instanceof PyIfStatement && ((PyIfStatement)element).getElsePart() != null) {
        elseBranch = ((PyIfStatement)element).getElsePart();
      }
      else {
        elseBranch = (PyElement)element;
      }
      unwrapElseBranch(elseBranch, element.getParent(), context);
    }
  protected abstract void unwrapElseBranch(PyElement branch, PsiElement parent, Context context) throws IncorrectOperationException;

}
