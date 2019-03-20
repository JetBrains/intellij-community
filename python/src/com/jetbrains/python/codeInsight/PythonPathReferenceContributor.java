// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceRegistrar;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

import static com.jetbrains.python.codeInsight.completion.PyPathCompletionContributor.pyStringLiteralMatches;

public class PythonPathReferenceContributor extends PsiReferenceContributor {
  /**
   * We try to resolve
   * 1. Relative path without slash are resolved when they either contain dot or are argument of function/method which is likely to accept
   * paths. Such functions consist of a) predefined list of functions (read_csv etc) b) such functions that their names comply to "file-ish" pattern like *path* or *file*
   * 2. Paths which contain slash and are not URLS except URLS of the form "file://*".
   * Relative paths are resolved against current folder and project root.
   * If path falls into above categories, but is not resolved, we ignore unresolved error.
   */
  @Override
  public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
    registrar
      .registerReferenceProvider(pyStringLiteralMatches("[/\\\\]", SystemInfo.isFileSystemCaseSensitive ? Pattern.CASE_INSENSITIVE : 0),
                                 new PythonPathReferenceProvider());
  }

  //public static PyElementPattern.Capture<PyStringLiteralExpression> pyStringLiteralMatches(final String regexp, int flags) {
  //  final Pattern pattern = Pattern.compile(regexp, flags);
  //  return new PyElementPattern.Capture<>(new InitialPatternCondition<PyStringLiteralExpression>(PyStringLiteralExpression.class) {
  //    @Override
  //    public boolean accepts(@Nullable Object o, ProcessingContext context) {
  //      final PyStringLiteralExpression expr = (PyStringLiteralExpression)o;
  //      // We complete only the last string element.
  //      if (!DocStringUtil.isDocStringExpression(expr) && expr.getTextLength() < STRING_LITERAL_LIMIT) {
  //        final String value = expr.getStringValue();
  //        return pattern.matcher(value).find();
  //      }
  //      return false;
  //    }
  //  });
  //}
}
