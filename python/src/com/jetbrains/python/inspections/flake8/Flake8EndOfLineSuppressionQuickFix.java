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

    // find last leaf on the current line
    PsiElement anchor = endElement;
    PsiComment existing = null;
    while (anchor != null) {
      final PsiElement next = PsiTreeUtil.nextLeaf(anchor);
      if (next == null) {
        break;
      }
      if (next instanceof PsiComment) {
        existing = (PsiComment)next;
        break;
      }
      final boolean isWhitespace = next instanceof PsiWhiteSpace;
      if (isWhitespace && next.textContains('\\')) {
        return;
      }
      final boolean isMultiline = next.textContains('\n');
      if (isMultiline) {
        if (isWhitespace) {
          break;
        }
        else {
          return;
        }
      }
      anchor = next;
    }

    if (existing != null) {
      final PsiComment combined = SuppressionUtil.createComment(project, "noqa " + existing.getText(), endElement.getLanguage());
      existing.replace(combined);
    }
    else {
      // we're reusing IntelliJ's code to create comments suitable for suppressions
      final PsiComment comment = SuppressionUtil.createComment(project, "noqa", endElement.getLanguage());
      if (anchor == null) {
        // e.g. when we're editing at the end of the file
        endElement.getContainingFile().add(comment);
      }
      else {
        // insert right after our anchor element, this will still be on the same line.
        // The whitespace with the newline is after our anchor element
        anchor.getParent().addAfter(comment, anchor);
      }  
    }
  }
}
