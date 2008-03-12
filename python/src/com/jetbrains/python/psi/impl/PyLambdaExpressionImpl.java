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
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyLambdaExpression;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyParameterList;
import com.jetbrains.python.psi.types.PyType;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 31.05.2005
 * Time: 0:26:59
 * To change this template use File | Settings | File Templates.
 */
public class PyLambdaExpressionImpl extends PyElementImpl implements PyLambdaExpression {
  public PyLambdaExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyLambdaExpression(this);
  }

  public PyType getType() {
    return null;
  }

  public PyParameterList getParameterList() {
    return childToPsiNotNull(PyElementTypes.PARAMETER_LIST_SET, 0);
  }

  public boolean processDeclarations(@NotNull final PsiScopeProcessor processor, @NotNull final ResolveState state, final PsiElement lastParent,
                                     @NotNull final PsiElement place) {
    PyParameter[] parameters = getParameterList().getParameters();
    for(PyParameter param: parameters) {
      if (param == lastParent) continue;
      if (!processor.execute(param, state)) return false;
    }
    return true;
  }
}
