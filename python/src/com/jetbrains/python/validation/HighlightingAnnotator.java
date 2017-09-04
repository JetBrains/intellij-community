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
package com.jetbrains.python.validation;

import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.Annotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.highlighting.PyHighlighter;
import com.jetbrains.python.psi.*;

import static com.jetbrains.python.psi.PyUtil.as;

/**
 * @author yole
 */
public class HighlightingAnnotator extends PyAnnotator {
  @Override
  public void visitPyParameter(PyParameter node) {
    PyFunction function = PsiTreeUtil.getParentOfType(node, PyFunction.class);
    if (function != null) {
      final PsiElement anchor = node.hasDefaultValue() ? node.getFirstChild() : node;
      final Annotation annotation = getHolder().createInfoAnnotation(anchor, null);
      annotation.setTextAttributes(node.isSelf() ? PyHighlighter.PY_SELF_PARAMETER : PyHighlighter.PY_PARAMETER);
    }
  }

  @Override
  public void visitPyReferenceExpression(PyReferenceExpression node) {
    final String referencedName = node.getReferencedName();
    if (!node.isQualified() && referencedName != null) {
      PyFunction function = PsiTreeUtil.getParentOfType(node, PyFunction.class);
      if (function != null) {
        final PyNamedParameter element = function.getParameterList().findParameterByName(referencedName);
        if (element != null) {
          Annotation annotation = getHolder().createInfoAnnotation(node, null);
          annotation.setTextAttributes(element.isSelf() ? PyHighlighter.PY_SELF_PARAMETER : PyHighlighter.PY_PARAMETER);
        }
      }
    }
  }

  @Override
  public void visitPyKeywordArgument(PyKeywordArgument node) {
    ASTNode keywordNode = node.getKeywordNode();
    if (keywordNode != null) {
      Annotation annotation = getHolder().createInfoAnnotation(keywordNode, null);
      annotation.setTextAttributes(PyHighlighter.PY_KEYWORD_ARGUMENT);
    }
  }

  @Override
  public void visitPyCallExpression(PyCallExpression node) {
    final PyReferenceExpression callee = as(node.getCallee(), PyReferenceExpression.class);
    if (callee != null) {
      final ASTNode functionName = callee.getNameElement();
      if (functionName != null) {
        final Annotation annotation = getHolder().createInfoAnnotation(functionName, null);
        annotation.setTextAttributes(PyHighlighter.PY_FUNCTION_CALL);
      }
    }
  }
}
