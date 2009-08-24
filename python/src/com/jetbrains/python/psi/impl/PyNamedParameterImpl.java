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
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.PsiElement;
import com.intellij.util.Icons;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.stubs.PyNamedParameterStub;
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
public class PyNamedParameterImpl extends PyPresentableElementImpl<PyNamedParameterStub> implements PyNamedParameter {
  public PyNamedParameterImpl(ASTNode astNode) {
    super(astNode);
  }

  public PyNamedParameterImpl(final PyNamedParameterStub stub) {
    super(stub, PyElementTypes.NAMED_PARAMETER);
  }

  @Nullable
  @Override
  public String getName() {
    final PyNamedParameterStub stub = getStub();
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
    pyVisitor.visitPyNamedParameter(this);
  }

  public boolean isPositionalContainer() {
    final PyNamedParameterStub stub = getStub();
    if (stub != null) {
      return stub.isPositionalContainer();
    }
    else {
      return getNode().findChildByType(PyTokenTypes.MULT) != null;
    }
  }

  public boolean isKeywordContainer() {
    final PyNamedParameterStub stub = getStub();
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

  @NotNull
  public String getRepr(boolean includeDefaultValue) {
    StringBuffer sb = new StringBuffer();
    if (isPositionalContainer()) sb.append("*");
    else if (isKeywordContainer()) sb.append("**");
    sb.append(getName());
    if (includeDefaultValue) {
      PyExpression default_v = getDefaultValue();
      if (default_v != null) sb.append("=").append(PyUtil.getReadableRepr(default_v, true));
    }
    return sb.toString();
  }

  public Icon getIcon(final int flags) {
    return Icons.PARAMETER_ICON;
  }

  public PyNamedParameter getAsNamed() {
    return this;
  }

  public PyTupleParameter getAsTuple() {
    return null; // we're not a tuple
  }

  public PyType getType() {
    if (getParent() instanceof PyParameterList) {
      PyParameterList parameterList = (PyParameterList) getParent();
      final PyParameter[] params = parameterList.getParameters();
      if (parameterList.getParent() instanceof PyFunction) {
        PyFunction func = (PyFunction) parameterList.getParent();
        if (params [0] == this) { // must be 'self'
          final PyClass containingClass = func.getContainingClass();
          if (containingClass != null) {
            return new PyClassType(containingClass, false); // TODO: check for @classmethod or @staticmethod node above us  
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

  @Override
  public String toString() {
    return super.toString() + "('" + getName() + "')";
  }
}
