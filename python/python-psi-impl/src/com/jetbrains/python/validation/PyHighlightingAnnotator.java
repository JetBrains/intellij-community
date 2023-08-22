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
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.TokenSet;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.highlighting.PyHighlighter;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public class PyHighlightingAnnotator extends PyAnnotator implements HighlightRangeExtension {

  @Override
  public void visitPyFunction(@NotNull PyFunction node) {
    if (node.isAsyncAllowed()) {
      highlightKeyword(node, PyTokenTypes.ASYNC_KEYWORD);
    }
    else {
      Optional
        .ofNullable(node.getNode())
        .map(astNode -> astNode.findChildByType(PyTokenTypes.ASYNC_KEYWORD))
        .ifPresent(asyncNode -> getHolder().newAnnotation(HighlightSeverity.ERROR,
                                                          PyPsiBundle.message("ANN.function.cannot.be.async", node.getName())).range(asyncNode).create());
    }
  }

  @Override
  public void visitPyNumericLiteralExpression(@NotNull PyNumericLiteralExpression node) {
    String suffix = node.getIntegerLiteralSuffix();
    if (suffix == null || "l".equalsIgnoreCase(suffix)) return;
    if (node.getContainingFile().getLanguage() != PythonLanguage.getInstance()) return;
    getHolder().newAnnotation(HighlightSeverity.ERROR, PyPsiBundle.message("INSP.python.trailing.suffix.not.support", suffix))
      .range(node).create();
  }

  @Override
  public void visitPyForStatement(@NotNull PyForStatement node) {
    highlightKeyword(node, PyTokenTypes.ASYNC_KEYWORD);
  }

  @Override
  public void visitPyWithStatement(@NotNull PyWithStatement node) {
    highlightKeyword(node, PyTokenTypes.ASYNC_KEYWORD);
  }

  @Override
  public void visitPyPrefixExpression(@NotNull PyPrefixExpression node) {
    highlightKeyword(node, PyTokenTypes.AWAIT_KEYWORD);
  }

  @Override
  public void visitPyComprehensionElement(@NotNull PyComprehensionElement node) {
    highlightKeywords(node, PyTokenTypes.ASYNC_KEYWORD);
  }

  @Override
  public void visitPyMatchStatement(@NotNull PyMatchStatement node) {
    highlightKeyword(node, PyTokenTypes.MATCH_KEYWORD);
  }

  @Override
  public void visitPyCaseClause(@NotNull PyCaseClause node) {
    highlightKeyword(node, PyTokenTypes.CASE_KEYWORD);
  }

  @Override
  public boolean isForceHighlightParents(@NotNull PsiFile file) {
    return file instanceof PyFile;
  }

  private void highlightKeyword(@NotNull PsiElement node, @NotNull PyElementType elementType) {
    highlightAsKeyword(node.getNode().findChildByType(elementType));
  }

  private void highlightKeywords(@NotNull PsiElement node, @NotNull PyElementType elementType) {
    for (ASTNode astNode : node.getNode().getChildren(TokenSet.create(elementType))) {
      highlightAsKeyword(astNode);
    }
  }

  private void highlightAsKeyword(@Nullable ASTNode astNode) {
    if (astNode != null) {
      getHolder().newSilentAnnotation(HighlightSeverity.INFORMATION).range(astNode)
      .textAttributes(PyHighlighter.PY_KEYWORD).create();
    }
  }
}
