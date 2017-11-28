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
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

/**
 * User : ktisha
 */
public class PyTryUnwrapper extends PyUnwrapper {
  public PyTryUnwrapper() {
    super(PyBundle.message("unwrap.try"));
  }

  public boolean isApplicableTo(@NotNull PsiElement e) {
    return e instanceof PyTryExceptStatement;
  }

  @Override
  protected void doUnwrap(final PsiElement element, final Context context) throws IncorrectOperationException {
    final PyTryExceptStatement statement = (PyTryExceptStatement)element;
    context.extractPart(statement);
    context.extractPart(statement.getElsePart());
    context.extractPart(statement.getFinallyPart());
    context.delete(statement);
  }
}

