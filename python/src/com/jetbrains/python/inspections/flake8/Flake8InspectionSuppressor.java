// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.flake8;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.intellij.codeInspection.InspectionSuppressor;
import com.intellij.codeInspection.SuppressQuickFix;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Locale;
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
public final class Flake8InspectionSuppressor implements InspectionSuppressor {
  public static final @NonNls String NOQA = "noqa";
  // See flake8.defaults module
  private static final Pattern NOQA_COMMENT_PATTERN = Pattern.compile("# noqa(?::\\s?(?<codes>([A-Z]+[0-9]+(?:[,\\s]+)?)+))?.*",
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
      .put("N801", "PyPep8Naming")  // class names should use CapWords convention
      .put("N802", "PyPep8Naming")  // function name should be lowercase
      .put("N803", "PyPep8Naming")  // argument name should be lowercase
      .put("N804", "PyPep8Naming")  // first argument of a classmethod should be named 'cls'
      .put("N805", "PyPep8Naming")  // first argument of a method should be named 'self'
      .put("N806", "PyPep8Naming")  // variable in function should be lowercase
      .put("N807", "PyPep8Naming")  // function name should not start and end with '__'
      .put("N811", "PyPep8Naming")  // constant imported as non constant
      .put("N812", "PyPep8Naming")  // lowercase imported as non lowercase
      .put("N813", "PyPep8Naming")  // camelcase imported as lowercase
      .put("N814", "PyPep8Naming")  // camelcase imported as constant
      .put("N815", "PyPep8Naming")  // mixedCase variable in class scope
      .put("N816", "PyPep8Naming")  // mixedCase variable in global scope
      .put("N817", "PyPep8Naming")  // camelcase imported as acronym
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
  public static @Nullable Set<String> extractNoqaCodes(@NotNull PsiComment comment) {
    String commentText = comment.getText();
    if (commentText == null) {
      return null;
    }

    int noqaOffset = commentText.toLowerCase(Locale.ENGLISH).indexOf("# noqa");
    if (noqaOffset < 0) {
      return null;
    }

    String noqaSuffix = commentText.substring(noqaOffset);
    Matcher matcher = NOQA_COMMENT_PATTERN.matcher(noqaSuffix);
    if (matcher.matches()) {
      String codeList = matcher.group("codes");
      return codeList == null ? Collections.emptySet() : Set.of(codeList.split("[,\\s]+"));
    }
    return null;
  }

  @Override
  public SuppressQuickFix @NotNull [] getSuppressActions(@Nullable PsiElement element, @NotNull String toolId) {
    return SuppressQuickFix.EMPTY_ARRAY;
  }
}
