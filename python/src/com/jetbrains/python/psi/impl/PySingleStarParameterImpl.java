/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PySingleStarParameter;
import com.jetbrains.python.psi.PyTupleParameter;
import com.jetbrains.python.psi.stubs.PySingleStarParameterStub;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PySingleStarParameterImpl extends PyPresentableElementImpl<PySingleStarParameterStub> implements PySingleStarParameter {
  public PySingleStarParameterImpl(ASTNode astNode) {
    super(astNode);
  }

  public PySingleStarParameterImpl(PySingleStarParameterStub stub) {
    super(stub, PyElementTypes.SINGLE_STAR_PARAMETER);
  }

  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    throw new UnsupportedOperationException();
  }

  public PyNamedParameter getAsNamed() {
    return null;
  }

  public PyTupleParameter getAsTuple() {
    return null;
  }

  public PyExpression getDefaultValue() {
    return null;
  }

  public boolean hasDefaultValue() {
    return false;
  }

  @Override
  public boolean isSelf() {
    return false;
  }
}
