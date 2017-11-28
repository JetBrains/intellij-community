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
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyDecorator;
import com.jetbrains.python.psi.PyDecoratorList;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.stubs.PyDecoratorListStub;
import org.jetbrains.annotations.NotNull;

/**
 * @author dcheryasov
 */
public class PyDecoratorListImpl extends PyBaseElementImpl<PyDecoratorListStub> implements PyDecoratorList{
  
  public PyDecoratorListImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyDecoratorList(this);
  }

  public PyDecoratorListImpl(final PyDecoratorListStub stub) {
    super(stub, PyElementTypes.DECORATOR_LIST);
  }

  @NotNull
  public PyDecorator[] getDecorators() {
    final PyDecorator[] decoarray = new PyDecorator[0];
    return getStubOrPsiChildren(PyElementTypes.DECORATOR_CALL, decoarray);
    //return decoarray;
  }

  @Override
  public PyDecorator findDecorator(String name) {
    final PyDecorator[] decorators = getDecorators();
    for (PyDecorator decorator : decorators) {
      final QualifiedName qName = decorator.getQualifiedName();
      if (qName != null && name.equals(qName.toString())) {
        return decorator;
      }
    }
    return null;
  }

  @Override
  public void deleteChildInternal(@NotNull ASTNode child) {
    final PsiElement childPsi = child.getPsi();
    if (childPsi instanceof PyDecorator && getDecorators().length == 1) {
      delete();
      return;
    }
    super.deleteChildInternal(child);
  }
}
