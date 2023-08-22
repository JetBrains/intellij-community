// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections.flake8;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Sets;
import com.intellij.codeInspection.InspectionSuppressor;
import com.intellij.codeInspection.SuppressQuickFix;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import one.util.streamex.StreamEx;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Suppress highlighting (errors, warning, unused, ...) which were added by Python inspections when "# noqa" is at the end of a line.
 * flake8 seems to do prefix checking, so both "# noqa123" is also valid.
 * We're replicating this behavior in this suppressor.
 *
 * @author jansorg
 */
public class Flake8InspectionSuppressor implements InspectionSuppressor {
  @NonNls public static final String NOQA = "noqa";
  // See flake8.defaults module
  private static final Pattern NOQA_COMMENT_PATTERN = Pattern.compile("# noqa(?::[\\s]?(?<codes>([A-Z]+[0-9]+(?:[,\\s]+)?)+))?.*",
                                                                      Pattern.CASE_INSENSITIVE);

  private static final ImmutableSetMultimap<String, String> ourInspectionToFlake8Code =
    ImmutableSetMultimap.<String, String>builder()
      .put("F401", "PyUnresolvedReferences")
      .put("F402", "PyShadowingNames")
      .put("F404", "PyFromFutureImport")
      .put("F407", "PyUnresolvedReferences")
      .put("F601", "PyDictDuplicateKeys")
      .put("F602", "PyDictDuplicateKeys")
      .put("F622", "PyTupleAssignmentBalance")
      .put("F811", "PyRedeclaration")
      .put("F812", "PyRedeclaration")
      .put("F821", "PyUnresolvedReferences")
      .put("F822", "PyUnresolvedReferences")
      .putAll("F823", "PyUnresolvedReferences", "PyUnboundLocalVariable")
      .put("F831", "PyUnusedLocal")
      .put("F841", "PyUnusedLocal")
      .put("C90", "PyUnusedLocal")
      // pycodestyle.py specific code
      .put("E711", "PyComparisonWithNone")
      .build()
      .inverse();

  @Override
  public boolean isSuppressedFor(@NotNull PsiElement element, @NotNull String toolId) {
    if (element instanceof PsiFileSystemItem || !(element.getContainingFile() instanceof PyFile)) {
      return false;
    }

    final PsiComment comment = PyPsiUtils.findSameLineComment(element);
    if (comment != null) {
      final Set<String> givenCodes = extractNoqaCodes(comment);
      if (givenCodes != null) {
        final ImmutableSet<String> knownCodes = ourInspectionToFlake8Code.get(toolId);
        return givenCodes.isEmpty() || StreamEx.of(knownCodes).cross(givenCodes).anyMatch((known, given) -> known.startsWith(given));
      }
    }
    return false;
  }

  /**
   * Extracts error codes from the specified "# noqa" comment.
   *
   * @return The list of explicit error codes if the comment has them, e.g. "# noqa: F821",
   * an empty list if it's "# noqa" comment but without any explicit codes,
   * {@code null} if the specified comment is not a "# noqa" comment.
   */
  @Nullable
  public static Set<String> extractNoqaCodes(@NotNull PsiComment comment) {
    String commentText = comment.getText();
    if (commentText == null) return null;
    int noqaOffset = StringUtils.lowerCase(commentText).indexOf("# noqa");
    String noqaSuffix = StringUtils.substring(commentText, noqaOffset);
    final Matcher matcher = NOQA_COMMENT_PATTERN.matcher(noqaSuffix);
    if (matcher.matches()) {
      final String codeList = matcher.group("codes");
      if (codeList != null) {
        return Sets.newHashSet(codeList.split("[,\\s]+"));
      }
      return Collections.emptySet();
    }
    return null;
  }

  @NotNull
  @Override
  public SuppressQuickFix[] getSuppressActions(@Nullable PsiElement element, @NotNull String toolId) {
    return SuppressQuickFix.EMPTY_ARRAY;
  }
}
