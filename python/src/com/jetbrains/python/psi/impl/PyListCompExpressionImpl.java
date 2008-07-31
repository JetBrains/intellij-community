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
import com.jetbrains.python.psi.ComprhForComponent;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyListCompExpression;
import com.jetbrains.python.psi.types.PyType;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 31.05.2005
 * Time: 23:33:16
 * To change this template use File | Settings | File Templates.
 */
public class PyListCompExpressionImpl extends PyComprehensionElementImpl implements PyListCompExpression {
  public PyListCompExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyListCompExpression(this);
  }

  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState substitutor,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    for (ComprhForComponent component : getForComponents()) {
      if (component != null) {
        //TODO: this needs to restrict resolution based on nesting
        // for example, this is not valid (the i in the first for should not resolve):
        //    x  for x in i for i in y
        if (!component.getIteratorVariable().processDeclarations(processor, substitutor, null, place)) return false;
      }
    }
    return true;
  }

  public PyType getType() {
    return null;
  }

}
