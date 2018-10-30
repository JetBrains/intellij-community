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
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.highlighting.PyHighlighter;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * @author vlan
 */
public class PyAsyncAwaitAnnorator extends PyAnnotator implements HighlightRangeExtension {

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
    highlightError(node, PyTokenTypes.ASYNC_KEYWORD, "'async' outside async function");
  }

  @Override
  public void visitPyWithStatement(PyWithStatement node) {
    highlightKeyword(node, PyTokenTypes.ASYNC_KEYWORD);
    highlightError(node, PyTokenTypes.ASYNC_KEYWORD, "'async' outside async function");
  }

  @Override
  public void visitPyPrefixExpression(PyPrefixExpression node) {
    highlightKeyword(node, PyTokenTypes.AWAIT_KEYWORD);
    highlightError(node, PyTokenTypes.AWAIT_KEYWORD, "'await' outside async function");
  }

  @Override
  public void visitPyComprehensionElement(PyComprehensionElement node) {
    highlightKeywords(node, PyTokenTypes.ASYNC_KEYWORD);
    highlightErrors(node, PyTokenTypes.ASYNC_KEYWORD, "'async' outside async function");
  }

  @Override
  public boolean isForceHighlightParents(@NotNull PsiFile file) {
    return file instanceof PyFile;
  }

  private void highlightKeyword(@NotNull PsiElement node, @NotNull PyElementType elementType) {
    if (LanguageLevel.forElement(node).isOlderThan(LanguageLevel.PYTHON37)) {
      highlightAsKeyword(node.getNode().findChildByType(elementType));
    }
  }

  private void highlightKeywords(@NotNull PsiElement node, @NotNull PyElementType elementType) {
    if (LanguageLevel.forElement(node).isOlderThan(LanguageLevel.PYTHON37)) {
      for (ASTNode astNode : node.getNode().getChildren(TokenSet.create(elementType))) {
        highlightAsKeyword(astNode);
      }
    }
  }

  private void highlightAsKeyword(@Nullable ASTNode astNode) {
    if (astNode != null) {
      final Annotation annotation = getHolder().createInfoAnnotation(astNode, null);
      annotation.setTextAttributes(PyHighlighter.PY_KEYWORD);
    }
  }

  private void highlightError(@NotNull PsiElement node, @NotNull PyElementType elementType, @NotNull String message) {
    if (insideNonAsyncFunctionInPython37AndLater(node)) {
      highlightAsError(node.getNode().findChildByType(elementType), message);
    }
  }

  private void highlightErrors(@NotNull PsiElement node, @NotNull PyElementType elementType, @NotNull String message) {
    if (insideNonAsyncFunctionInPython37AndLater(node)) {
      for (ASTNode child : node.getNode().getChildren(TokenSet.create(elementType))) {
        highlightAsError(child, message);
      }
    }
  }

  private void highlightAsError(@Nullable ASTNode astNode, @NotNull String message) {
    if (astNode != null) {
      getHolder().createErrorAnnotation(astNode, message);
    }
  }

  private static boolean insideNonAsyncFunctionInPython37AndLater(@NotNull PsiElement node) {
    if (LanguageLevel.forElement(node).isAtLeast(LanguageLevel.PYTHON37)) {
      // In other Python versions `await` and `async` will be correctly highlighted as errors
      // due to StatementParsing.filter(IElementType, int, int, CharSequence, boolean)

      final PyFunction function = PsiTreeUtil.getParentOfType(node, PyFunction.class, true, ScopeOwner.class);
      return function != null && !function.isAsync();
    }

    return false;
  }
}
