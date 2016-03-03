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

import com.google.common.collect.Lists;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiNamedElement;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyCollectionTypeImpl;
import com.jetbrains.python.psi.types.PyNoneType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
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
      final List<PyType> parameters = Arrays.asList(context.getType(resultExpr), null, PyNoneType.INSTANCE);
      return new PyCollectionTypeImpl(generator, false, parameters);
    }
    return null;
  }

  @NotNull
  public List<PsiNamedElement> getNamedElements() {
    // extract whatever names are defined in "for" components
    List<ComprhForComponent> fors = getForComponents();
    PyExpression[] for_targets = new PyExpression[fors.size()];
    int i = 0;
    for (ComprhForComponent for_comp : fors) {
      for_targets[i] = for_comp.getIteratorVariable();
      i += 1;
    }
    final List<PyExpression> expressions = PyUtil.flattenedParensAndStars(for_targets);
    final List<PsiNamedElement> results = Lists.newArrayList();
    for (PyExpression expression : expressions) {
      if (expression instanceof PsiNamedElement) {
        results.add((PsiNamedElement)expression);
      }
    }
    return results;
  }

  @Nullable
  public PsiNamedElement getNamedElement(@NotNull final String the_name) {
    return PyUtil.IterHelper.findName(getNamedElements(), the_name);
  }
}
