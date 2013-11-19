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
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyCollectionTypeImpl;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class PyGeneratorExpressionImpl extends PyComprehensionElementImpl implements PyGeneratorExpression {
  public PyGeneratorExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyGeneratorExpression(this);
  }

  @Nullable
  @Override
  public PyType getType(@NotNull TypeEvalContext context, @NotNull TypeEvalContext.Key key) {
    final PyExpression resultExpr = getResultExpression();
    final PyBuiltinCache cache = PyBuiltinCache.getInstance(this);
    final PyClass generator = cache.getClass(PyNames.FAKE_GENERATOR);
    if (resultExpr != null && generator != null) {
      final PyType elementType = context.getType(resultExpr);
      return new PyCollectionTypeImpl(generator, false, elementType);
    }
    return null;
  }

  @NotNull
  public Iterable<PyElement> iterateNames() {
    // extract whatever names are defined in "for" components
    List<ComprhForComponent> fors = getForComponents();
    PyExpression[] for_targets = new PyExpression[fors.size()];
    int i = 0;
    for (ComprhForComponent for_comp : fors) {
      for_targets[i] = for_comp.getIteratorVariable();
      i += 1;
    }
    return new ArrayList<PyElement>(PyUtil.flattenedParensAndStars(for_targets));
  }

  public PsiElement getElementNamed(final String the_name) {
    return IterHelper.findName(iterateNames(), the_name);
  }

  public boolean mustResolveOutside() {
    return false;
  }

}
