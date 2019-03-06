// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections.flake8;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.SuppressQuickFix;
import com.intellij.codeInspection.SuppressionUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This adds a end-of-line # noqa comment on the same line as the highlighted element.
 *
 * @author jansorg
 */
public class Flake8EndOfLineSuppressionQuickFix implements SuppressQuickFix {
  @Override
  public boolean isAvailable(@NotNull Project project, @NotNull PsiElement psiElement) {
    // always available because it's only added when we know we're suppressing something
    return true;
  }

  @Override
  public boolean isSuppressAll() {
    return false;
  }

  @Override
  public boolean startInWriteAction() {
    // true because we're modifying the PSI structure
    return true;
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getName() {
    return "Suppress with flake8 # noqa";
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getFamilyName() {
    return "Python";
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor problemDescriptor) {
    final PsiElement endElement = problemDescriptor.getEndElement();
    final PsiElement commentOrAnchor = findSameLineCommentOrPrecedingAnchorElement(endElement);

    if (commentOrAnchor instanceof PsiComment) {
      final PsiComment existing = (PsiComment)commentOrAnchor;
      final PsiComment combined = SuppressionUtil.createComment(project, "noqa " + existing.getText(), endElement.getLanguage());
      existing.replace(combined);
    }
    else if (commentOrAnchor != null) {
      // we're reusing IntelliJ's code to create comments suitable for suppressions
      final PsiComment comment = SuppressionUtil.createComment(project, "noqa", endElement.getLanguage());
      // insert right after our anchor element, this will still be on the same line.
      // The whitespace with the newline is after our anchor element
      commentOrAnchor.getParent().addAfter(comment, commentOrAnchor);
    }
  }

  /**
   * Returns either the line comment itself for the line where {@code elem} is located or
   * the last non-whitespace element on its line intended to be used as an anchor for {@link PsiElement#addAfter(PsiElement, PsiElement)}.
   * {@code null} means that there is no such comment and it cannot be inserted.
   */
  @Nullable
  static PsiElement findSameLineCommentOrPrecedingAnchorElement(@NotNull PsiElement elem) {
    PsiElement anchor = elem;
    while (true) {
      final PsiElement next = PsiTreeUtil.nextLeaf(anchor);
      if (next == null) {
        break;
      }
      if (next instanceof PsiComment) {
        return next;
      }
      final boolean isWhitespace = next instanceof PsiWhiteSpace;
      if (isWhitespace && next.textContains('\\')) {
        return null;
      }
      final boolean isMultiline = next.textContains('\n');
      if (isMultiline) {
        if (isWhitespace) {
          break;
        }
        else {
          return null;
        }
      }
      anchor = next;
    }
    return anchor;
  }
}
