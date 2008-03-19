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

package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.util.Icons;
import com.intellij.util.IncorrectOperationException;
import com.intellij.openapi.extensions.Extensions;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.stubs.PyParameterStub;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 29.05.2005
 * Time: 23:04:59
 * To change this template use File | Settings | File Templates.
 */
public class PyParameterImpl extends PyPresentableElementImpl<PyParameterStub> implements PyParameter {
  public PyParameterImpl(ASTNode astNode) {
    super(astNode);
  }

  public PyParameterImpl(final PyParameterStub stub) {
    super(stub, PyElementTypes.FORMAL_PARAMETER);
  }

  @Nullable
  @Override
  public String getName() {
    final PyParameterStub stub = getStub();
    if (stub != null) {
      return stub.getName();
    }
    else {
      ASTNode node = getNode().findChildByType(PyTokenTypes.IDENTIFIER);
      return node != null ? node.getText() : null;
    }
  }

  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    final ASTNode nameElement = getLanguage().getElementGenerator().createNameIdentifier(getProject(), name);
    getNode().replaceChild(getNode().getFirstChildNode(), nameElement);
    return this;
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyParameter(this);
  }

  public boolean isPositionalContainer() {
    final PyParameterStub stub = getStub();
    if (stub != null) {
      return stub.isPositionalContainer();
    }
    else {
      return getNode().findChildByType(PyTokenTypes.MULT) != null;
    }
  }

  public boolean isKeywordContainer() {
    final PyParameterStub stub = getStub();
    if (stub != null) {
      return stub.isKeywordContainer();
    }
    else {
      return getNode().findChildByType(PyTokenTypes.EXP) != null;
    }
  }

  public
  @Nullable
  PyExpression getDefaultValue() {
    ASTNode[] nodes = getNode().getChildren(PyElementTypes.EXPRESSIONS);
    if (nodes.length > 0) {
      return (PyExpression)nodes[0].getPsi();
    }
    return null;
  }

  public Icon getIcon(final int flags) {
    return Icons.PARAMETER_ICON;
  }

  public PyType getType() {
    if (getParent() instanceof PyParameterList) {
      PyParameterList parameterList = (PyParameterList) getParent();
      final PyParameter[] params = parameterList.getParameters();
      if (parameterList.getParent() instanceof PyFunction) {
        PyFunction func = (PyFunction) parameterList.getParent();
        if (params [0] == this) {
          final PyClass containingClass = func.getContainingClass();
          if (containingClass != null) {
            return new PyClassType(containingClass);
          }
        }
        for(PyTypeProvider provider: Extensions.getExtensions(PyTypeProvider.EP_NAME)) {
          PyType result = provider.getParameterType(this, func);
          if (result != null) return result;
        }
      }
    }
    return null;
  }
}
