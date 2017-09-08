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
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.highlighting.PyHighlighter;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;

import static com.jetbrains.python.psi.PyUtil.as;

/**
 * @author yole
 */
public class HighlightingAnnotator extends PyAnnotator {
  @Override
  public void visitPyParameter(PyParameter node) {
    final PyFunction function = PsiTreeUtil.getParentOfType(node, PyFunction.class);
    if (function != null) {
      final TextAttributesKey attrKey = node.isSelf() ? PyHighlighter.PY_SELF_PARAMETER : PyHighlighter.PY_PARAMETER;
      addHighlightingAnnotation(node.getFirstChild(), attrKey);
    }
  }

  @Override
  public void visitPyReferenceExpression(PyReferenceExpression node) {
    final String referencedName = node.getReferencedName();
    if (!node.isQualified() && referencedName != null) {
      final PyFunction function = PsiTreeUtil.getParentOfType(node, PyFunction.class);
      if (function != null) {
        final PyNamedParameter element = function.getParameterList().findParameterByName(referencedName);
        if (element != null) {
          final TextAttributesKey attrKey = element.isSelf() ? PyHighlighter.PY_SELF_PARAMETER : PyHighlighter.PY_PARAMETER;
          addHighlightingAnnotation(node, attrKey);
        }
      }
    }
  }

  @Override
  public void visitPyKeywordArgument(PyKeywordArgument node) {
    final ASTNode keywordNode = node.getKeywordNode();
    if (keywordNode != null) {
      addHighlightingAnnotation(keywordNode, PyHighlighter.PY_KEYWORD_ARGUMENT);
    }
  }

  @Override
  public void visitPyCallExpression(PyCallExpression node) {
    final PyReferenceExpression callee = as(node.getCallee(), PyReferenceExpression.class);
    if (callee != null) {
      if (!callee.isQualified() && PyBuiltinCache.isInBuiltins(callee)) {
        return;
      }
      final ASTNode functionName = callee.getNameElement();
      if (functionName != null) {
        final TextAttributesKey attrKey = callee.isQualified() ? PyHighlighter.PY_METHOD_CALL : PyHighlighter.PY_FUNCTION_CALL;
        addHighlightingAnnotation(functionName, attrKey);
      }
    }
  }

  @Override
  public void visitPyAnnotation(PyAnnotation node) {
    final PyExpression value = node.getValue();
    if (value != null) {
      addHighlightingAnnotation(value, PyHighlighter.PY_ANNOTATION);
    }
  }

  @Override
  public void visitElement(PsiElement element) {
    // Highlight None, True and False as keywords once again inside annotations after PyHighlighter
    // to keep their original color similarly to builtin symbols.
    if (PyTokenTypes.EXPRESSION_KEYWORDS.contains(element.getNode().getElementType()) &&
        PsiTreeUtil.getParentOfType(element, PyAnnotation.class) != null) {
      addHighlightingAnnotation(element, PyHighlighter.PY_KEYWORD);
    }
  }
}
