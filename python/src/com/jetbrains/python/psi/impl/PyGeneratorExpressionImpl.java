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
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyType;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 05.06.2005
 * Time: 10:16:07
 * To change this template use File | Settings | File Templates.
 */
public class PyGeneratorExpressionImpl extends PyComprehensionElementImpl implements PyGeneratorExpression {
  public PyGeneratorExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyGeneratorExpression(this);
  }

  public PyType getType() {
    return null;
  }

  @NotNull
  public Iterable<PyElement> iterateNames() {
    // extract whatever names are defined in "for" components
    List<ComprhForComponent> fors = getForComponents();
    PyElement[] for_targets = new PyElement[fors.size()];
    int i = 0;
    for (ComprhForComponent for_comp : fors) {
      for_targets[i] = for_comp.getIteratorVariable();
      i += 1;
    }
    List<PyElement> name_refs = PyUtil.flattenedParens(for_targets);
    return name_refs;
  }

  public PsiElement getElementNamed(final String the_name) {
    return IterHelper.findName(iterateNames(), the_name);
  }

  public boolean mustResolveOutside() {
    return false;
  }

}
