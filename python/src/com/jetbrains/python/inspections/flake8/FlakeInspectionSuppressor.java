// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections.flake8;

import com.intellij.codeInspection.InspectionSuppressor;
import com.intellij.codeInspection.SuppressQuickFix;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyTokenTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Suppress highlighting (errors, warning, unused, ...) which were added by Python inspections when either
 * # noqa is at the end of a line or
 * # flake8: noqa is on a single line anywhere in a Python file.
 * flake8 seems to do prefix checking, so both "# noqa123" and "# flake8: noqa123" are also valid.
 * We're replicating this behavior in this suppressor.
 *
 * @author jansorg
 */
public class FlakeInspectionSuppressor implements InspectionSuppressor {
  @Override
  public boolean isSuppressedFor(@NotNull PsiElement element, @NotNull String toolId) {
    // suppress Python inspections only
    // this might be a bit too eager here, we'll see if there are use cases to also suppress other types of inspections
    if (!toolId.startsWith("Py")) {
      return false;
    }

    return isSuppressedByEndOfLine(element) || isSuppressedByLineComment(element);
  }

  private boolean isSuppressedByEndOfLine(PsiElement element) {
    // a comment token is always a leaf, the start element is never the comment which is suppressing our warning
    for (PsiElement leaf = PsiTreeUtil.nextLeaf(element); leaf != null; leaf = PsiTreeUtil.nextLeaf(leaf)) {
      // end of line reached without a comment before, returning false to not suppress
      if (leaf instanceof PsiWhiteSpace && leaf.textContains('\n')) {
        return false;
      }

      // the leaf is a comment and still on the same line
      // return true to suppress if it's containing our marker text
      if (isFlakeLineMarker(leaf, "# noqa")) {
        return true;
      }
    }

    // happens when the end of a file was reached, for example
    return false;
  }

  /**
   * Locate line comments matching # flake8: noqa
   * This has to be fast as it's called a lot of times.
   *
   * @param element our start element containing the error element
   * @return {@code true} if the highlighting should be suppressed, {@code false} otherwise
   */
  private boolean isSuppressedByLineComment(PsiElement element) {
    final PsiSearchHelper searchHelper = PsiSearchHelper.getInstance(element.getContainingFile().getProject());
    final GlobalSearchScope fileScope = GlobalSearchScope.fileScope(element.getContainingFile());
    return !searchHelper.processCommentsContainingIdentifier("flake8", fileScope,
                                                             comment -> !isFlakeLineMarker(comment, "# flake8: noqa"));
  }

  /**
   * A hopefully better test for a comment token than using instanceof.
   *
   * @param element The element to check
   * @param prefix  The prefix which must match the comment's text
   * @return {@code true} if it's a comment starting with the prefix
   */
  private boolean isFlakeLineMarker(PsiElement element, String prefix) {
    // this is equivalent to "element instanceof PsiComment && element.getText().startWidth(prefix)"
    // for now we assume that the equality check is faster than the instanceof check
    ASTNode node = element.getNode();
    return node != null
           && node.getElementType() == PyTokenTypes.END_OF_LINE_COMMENT
           && element.getText().startsWith(prefix);
  }

  @NotNull
  @Override
  public SuppressQuickFix[] getSuppressActions(@Nullable PsiElement element, @NotNull String toolId) {
    return new SuppressQuickFix[]{
      new Flake8EndOfLineSuppressionQuickFix()
    };
  }
}
