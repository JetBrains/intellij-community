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
import com.intellij.psi.tree.TokenSet;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyParameterList;
import com.jetbrains.python.psi.stubs.PyParameterListStub;
import com.jetbrains.python.psi.stubs.PyParameterStub;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 29.05.2005
 * Time: 23:03:25
 * To change this template use File | Settings | File Templates.
 */
public class PyParameterListImpl extends PyBaseElementImpl<PyParameterListStub> implements PyParameterList {

  private final TokenSet PARAMETER_FILTER = TokenSet.create(PyElementTypes.FORMAL_PARAMETER);

  public PyParameterListImpl(ASTNode astNode) {
    super(astNode);
  }

  public PyParameterListImpl(final PyParameterListStub stub) {
    super(stub, PyElementTypes.PARAMETER_LIST);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyParameterList(this);
  }

  public PyParameter[] getParameters() {
    final PyParameterListStub stub = getStub();
    if (stub != null) {
      final PyParameterStub[] paramStubs = stub.getParameters();
      PyParameter[] params = new PyParameter[paramStubs.length];
      for (int i = 0; i < paramStubs.length; i++) {
        PyParameterStub paramStub = paramStubs[i];
        params[i] = paramStub.getPsi();
      }
      return params;
    }
    else {
      final ASTNode[] nodes = getNode().getChildren(PARAMETER_FILTER);
      final PyParameter[] params = new PyParameter[nodes.length];
      for (int i = 0; i < params.length; i++) {
        params[i] = (PyParameter)nodes[i].getPsi();
      }
      return params;
    }
  }
}
