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
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.util.ArrayUtil;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.stubs.PyParameterListStub;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class PyParameterListImpl extends PyBaseElementImpl<PyParameterListStub> implements PyParameterList {
  public PyParameterListImpl(ASTNode astNode) {
    super(astNode);
  }

  public PyParameterListImpl(final PyParameterListStub stub) {
    this(stub, PyElementTypes.PARAMETER_LIST);
  }

  public PyParameterListImpl(final PyParameterListStub stub, IStubElementType nodeType) {
    super(stub, nodeType);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyParameterList(this);
  }

  public PyParameter[] getParameters() {
    return getStubOrPsiChildren(PythonDialectsTokenSetProvider.INSTANCE.getParameterTokens(), new PyParameter[0]);
  }

  public void addParameter(final PyNamedParameter param) {
    PsiElement paren = getLastChild();
    if (paren != null && ")".equals(paren.getText())) {
      ASTNode beforeWhat = paren.getNode(); // the closing paren will be this
      PyParameter[] params = getParameters();
      final boolean hasDefaultValue = param.hasDefaultValue();
      boolean isLast = true;
      for (PyParameter p : params) {
        if (!hasDefaultValue && p.hasDefaultValue()) {
          beforeWhat = p.getNode();
          isLast = false;
          break;
        }
        if (p instanceof PyNamedParameter) {
          PyNamedParameter named = (PyNamedParameter)p;
          if (named.isKeywordContainer() || named.isPositionalContainer()) {
            beforeWhat = p.getNode();
            isLast = false;
            break;
          }
        }
      }
      final ASTNode previous = PyPsiUtils.getPrevNonWhitespaceSibling(beforeWhat);
      PyUtil.addListNode(this, param, beforeWhat, !isLast || params.length == 0 ||
                                          previous.getElementType() == PyTokenTypes.COMMA, isLast,
                                          beforeWhat.getElementType() != PyTokenTypes.RPAR);
    }
  }

  public boolean hasPositionalContainer() {
    for (PyParameter parameter: getParameters()) {
      if (parameter instanceof PyNamedParameter && ((PyNamedParameter) parameter).isPositionalContainer()) {
        return true;
      }
    }
    return false;
  }

  public boolean hasKeywordContainer() {
    for (PyParameter parameter: getParameters()) {
      if (parameter instanceof PyNamedParameter && ((PyNamedParameter) parameter).isKeywordContainer()) {
        return true;
      }
    }
    return false;
  }

  @Override
  @Nullable
  public PyNamedParameter findParameterByName(@NotNull final String name) {
    final Ref<PyNamedParameter> result = new Ref<>();
    ParamHelper.walkDownParamArray(getParameters(), new ParamHelper.ParamVisitor() {
      @Override
      public void visitNamedParameter(PyNamedParameter param, boolean first, boolean last) {
        if (name.equals(param.getName())) {
          result.set(param);
        }
      }
    });
    return result.get();
  }

  public String getPresentableText(final boolean includeDefaultValue) {
    final StringBuilder target = new StringBuilder();
    final String COMMA = ", ";
    target.append("(");
    ParamHelper.walkDownParamArray(
      getParameters(),
      new ParamHelper.ParamWalker() {
        public void enterTupleParameter(PyTupleParameter param, boolean first, boolean last) {
          target.append("(");
        }

        public void leaveTupleParameter(PyTupleParameter param, boolean first, boolean last) {
          target.append(")");
          if (!last) target.append(COMMA);
        }

        public void visitNamedParameter(PyNamedParameter param, boolean first, boolean last) {
          target.append(param.getRepr(includeDefaultValue));
          if (!last) target.append(COMMA);
        }

        public void visitSingleStarParameter(PySingleStarParameter param, boolean first, boolean last) {
          target.append('*');
          if (!last) target.append(COMMA);
        }
      }
    );
    target.append(")");
    return target.toString();
  }

  @Nullable
  @Override
  public PyFunction getContainingFunction() {
    final PsiElement parent = getParentByStub();
    return parent instanceof PyFunction ? (PyFunction) parent : null;
  }

  @Override
  public void deleteChildInternal(@NotNull ASTNode node) {
    if (ArrayUtil.contains(node.getPsi(), getParameters())) {
      PyPsiUtils.deleteAdjacentCommaWithWhitespaces(this, node.getPsi());
    }
    super.deleteChildInternal(node);
  }
}
