// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections.flake8;

import com.google.common.collect.ImmutableSet;
import com.intellij.codeInspection.InspectionSuppressor;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.SuppressQuickFix;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.util.ReflectionUtil;
import com.jetbrains.python.codeInsight.typing.PyStubPackagesAdvertiser;
import com.jetbrains.python.codeInsight.typing.PyStubPackagesCompatibilityInspection;
import com.jetbrains.python.inspections.PyInterpreterInspection;
import com.jetbrains.python.inspections.PyPackageRequirementsInspection;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Suppress highlighting (errors, warning, unused, ...) which were added by Python inspections when either
 * # noqa is at the end of a line or
 * # flake8: noqa is on a single line anywhere in a Python file.
 * flake8 seems to do prefix checking, so both "# noqa123" and "# flake8: noqa123" are also valid.
 * We're replicating this behavior in this suppressor.
 *
 * @author jansorg
 */
public class Flake8InspectionSuppressor implements InspectionSuppressor {
  private static final ImmutableSet<Class<? extends LocalInspectionTool>> INSPECTION_WHITELIST = ImmutableSet.of(
    PyInterpreterInspection.class,
    PyPackageRequirementsInspection.class,
    PyStubPackagesAdvertiser.class,
    PyStubPackagesCompatibilityInspection.class
  );

  private static final Set<String> ourWhitelistedInspectionIds =
    StreamEx.of(INSPECTION_WHITELIST)
      .map(ReflectionUtil::newInstance)
      .map(LocalInspectionTool::getID)
      .toImmutableSet();
  
  @Override
  public boolean isSuppressedFor(@NotNull PsiElement element, @NotNull String toolId) {
    // suppress Python inspections only
    // this might be a bit too eager here, we'll see if there are use cases to also suppress other types of inspections
    if (!toolId.startsWith("Py") || ourWhitelistedInspectionIds.contains(toolId)) {
      return false;
    }

    return isSuppressedByEndOfLine(element) || isSuppressedByLineComment(element);
  }

  private boolean isSuppressedByEndOfLine(PsiElement element) {
    // a comment token is always a leaf, the start element is never the comment which is suppressing our warning
    final PsiElement commentOrAnchor = Flake8EndOfLineSuppressionQuickFix.findSameLineCommentOrPrecedingAnchorElement(element);
    return commentOrAnchor instanceof PsiComment && isFlakeLineMarker(commentOrAnchor, "# noqa");
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
    return element instanceof PsiComment && element.getText().startsWith(prefix);
  }

  @NotNull
  @Override
  public SuppressQuickFix[] getSuppressActions(@Nullable PsiElement element, @NotNull String toolId) {
    return new SuppressQuickFix[]{
      new Flake8EndOfLineSuppressionQuickFix()
    };
  }
}
