/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.codeInsight.daemon.impl.HighlightRangeExtension;
import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.Annotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.highlighting.PyHighlighter;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * @author vlan
 */
public class DumbAwareHighlightingAnnotator extends PyAnnotator implements HighlightRangeExtension {

  @Override
  public void visitPyFunction(PyFunction node) {
    if (node.isAsyncAllowed()) {
      highlightKeyword(node, PyTokenTypes.ASYNC_KEYWORD);
    }
    else {
      Optional
        .ofNullable(node.getNode())
        .map(astNode -> astNode.findChildByType(PyTokenTypes.ASYNC_KEYWORD))
        .ifPresent(asyncNode -> getHolder().createErrorAnnotation(asyncNode, "function \"" + node.getName() + "\" cannot be async"));
    }
  }

  @Override
  public void visitPyForStatement(PyForStatement node) {
    highlightKeyword(node, PyTokenTypes.ASYNC_KEYWORD);
  }

  @Override
  public void visitPyWithStatement(PyWithStatement node) {
    highlightKeyword(node, PyTokenTypes.ASYNC_KEYWORD);
  }

  @Override
  public void visitPyPrefixExpression(PyPrefixExpression node) {
    highlightKeyword(node, PyTokenTypes.AWAIT_KEYWORD);
  }

  @Override
  public void visitPyComprehensionElement(PyComprehensionElement node) {
    PsiTreeUtil
      .collectElementsOfType(node.getResultExpression(), PyPrefixExpression.class)
      .stream()
      .filter(expression -> expression.getOperator() == PyTokenTypes.AWAIT_KEYWORD && expression.getOperand() != null)
      .map(expression -> expression.getNode().findChildByType(PyTokenTypes.AWAIT_KEYWORD))
      .filter(awaitNode -> awaitNode != null)
      .forEach(awaitNode -> getHolder().createErrorAnnotation(awaitNode, "'await' expressions are not supported here"));
  }

  @Override
  public boolean isForceHighlightParents(@NotNull PsiFile file) {
    return file instanceof PyFile;
  }

  private void highlightKeyword(@NotNull PsiElement node, @NotNull PyElementType elementType) {
    final ASTNode astNode = node.getNode().findChildByType(elementType);
    if (astNode != null) {
      final Annotation annotation = getHolder().createInfoAnnotation(astNode, null);
      annotation.setTextAttributes(PyHighlighter.PY_KEYWORD);
    }
  }
}
