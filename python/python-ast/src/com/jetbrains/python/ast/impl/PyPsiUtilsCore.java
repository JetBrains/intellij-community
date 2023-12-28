// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.jetbrains.python.ast.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedList;
import java.util.List;

@ApiStatus.Experimental
public final class PyPsiUtilsCore {
  private PyPsiUtilsCore() {
  }

  @ApiStatus.Internal
  public static <T extends PyAstElement> T @NotNull [] nodesToPsi(ASTNode[] nodes, T[] array) {
    T[] psiElements = ArrayUtil.newArray(ArrayUtil.getComponentType(array), nodes.length);
    for (int i = 0; i < nodes.length; i++) {
      //noinspection unchecked
      psiElements[i] = (T)nodes[i].getPsi();
    }
    return psiElements;
  }

  /**
   * Finds first sibling that is neither comment, nor whitespace after given element.
   * @param strict prohibit returning element itself
   */
  @Nullable
  public static PsiElement getNextNonCommentSibling(@Nullable PsiElement start, boolean strict) {
    if (!strict && !(start instanceof PsiWhiteSpace || start instanceof PsiComment)) {
      return start;
    }
    return PsiTreeUtil.skipWhitespacesAndCommentsForward(start);
  }

  /**
   * Returns child element in the psi tree
   *
   * @param filter  Types of expected child
   * @param number  number
   * @param element tree parent node
   * @return PsiElement - child psiElement
   */
  @Nullable
  public static PsiElement getChildByFilter(@NotNull PsiElement element, @NotNull TokenSet filter, int number) {
    final ASTNode node = element.getNode();
    if (node != null) {
      final ASTNode[] children = node.getChildren(filter);
      return (0 <= number && number < children.length) ? children[number].getPsi() : null;
    }
    return null;
  }

  @Nullable
  public static String strValue(@Nullable PyAstExpression expression) {
    return expression instanceof PyAstStringLiteralExpression ? ((PyAstStringLiteralExpression)expression).getStringValue() : null;
  }

  @Nullable
  public static QualifiedName asQualifiedName(@Nullable PyAstExpression expr) {
    return expr instanceof PyAstQualifiedExpression ? ((PyAstQualifiedExpression)expr).asQualifiedName() : null;
  }

  @Nullable
  public static QualifiedName asQualifiedName(@NotNull PyAstQualifiedExpression expr) {
    final List<String> path = new LinkedList<>();
    final String firstName = expr.getReferencedName();
    if (firstName == null) {
      return null;
    }
    path.add(firstName);
    PyAstExpression qualifier = expr.getQualifier();
    while (qualifier != null) {
      final PyAstReferenceExpression qualifierReference = ObjectUtils.tryCast(qualifier, PyAstReferenceExpression.class);
      if (qualifierReference == null) {
        return null;
      }
      final String qualifierName = qualifierReference.getReferencedName();
      if (qualifierName == null) {
        return null;
      }
      path.add(0, qualifierName);
      qualifier = qualifierReference.getQualifier();
    }
    return QualifiedName.fromComponents(path);
  }

  /**
   * Wrapper for {@link PsiUtilCore#ensureValid(PsiElement)} that skips nulls
   */
  public static void assertValid(@Nullable final PsiElement element) {
    if (element == null) {
      return;
    }
    PsiUtilCore.ensureValid(element);
  }
}
