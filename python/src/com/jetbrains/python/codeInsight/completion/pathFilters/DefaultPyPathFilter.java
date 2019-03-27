// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.completion.pathFilters;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.documentation.docstrings.DocStringUtil;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyStringLiteralExpression;

import java.util.*;
import java.util.regex.Pattern;

/**
 *
 * 1. Relative path without slash are resolved when they are argument of function/method which is likely to accept
 * paths. Such functions consist of<br>
 * a) predefined list of functions (read_csv etc);<br>
 * b) such functions that their names comply to "file-ish" pattern like *path* or *file*;<p>
 *
 * 2. Paths which contain file separator.
 * Relative paths are resolved against current folder and project root.
 */
public class DefaultPyPathFilter implements PyPathFilter {
  private static final Pattern
    FILTERING_PATTERN = Pattern.compile("[/\\\\]", SystemInfo.isFileSystemCaseSensitive ? Pattern.CASE_INSENSITIVE : 0);
  private static final int STRING_LITERAL_LIMIT = 10_000;

  /** For this functions every String will be treated as a candidate for path. */
  private static final Collection<String> DEFAULT_FUNCTIONS_TO_CHECK = new HashSet<>(Arrays.asList(
    "read_csv",
    "open"
  ));

  /** For functions with names complying thus pattern every string will be treated as a candidate for path. */
  private static final Pattern FUNCTION_NAME_PATTERN = Pattern.compile("((file)|(path))");

  @Override
  public boolean test(PyStringLiteralExpression expr) {
    if (checkFunction(expr)) {
      return true;
    }

    if (!DocStringUtil.isDocStringExpression(expr) && expr.getTextLength() < STRING_LITERAL_LIMIT) {
      final String value = expr.getStringValue();
      return FILTERING_PATTERN.matcher(value).find();
    }
    return false;
  }

  public static boolean checkFunction(PyStringLiteralExpression expr) {
    Optional<PyCallExpression> call = getAncestorByBackwardPath(expr, PyCallExpression.class, PyArgumentList.class);

    return call
      .map(c -> c.getCallee() != null && DEFAULT_FUNCTIONS_TO_CHECK.contains(c.getCallee().getName()) || FUNCTION_NAME_PATTERN.matcher(
        Objects.requireNonNull(c.getCallee().getName())).matches())
      .orElse(false);
  }

  /**
   * Gets ancestor in the end of chain el -> ancestors[0] -> ancestors[1] -> ... -> ancestors[n] -> topmostAncestor,
   * where class of ancestor[i] should correspond to class reversedAncestorsClasses[n - i]. If somewhere the chain breaks (there is no
   * ancestor at all ot it is of a wrong class), {@link Optional#empty()} is returned.
   *
   * @param el Element.
   * @param topmostAncestorClass Topmost ancestor class.
   * @param reversedAncestorsClasses Classes of ancestors specified in reversed order.
   * @param <T> Type of topmost ancestor.
   * @return
   */
  @SuppressWarnings("unchecked")
  private static <T extends PsiElement> Optional<T> getAncestorByBackwardPath(PsiElement el,
                                                                              Class<T> topmostAncestorClass,
                                                                              Class<? extends PsiElement> ... reversedAncestorsClasses) {
    PsiElement cur = el;

    PsiElement parent;
    if (reversedAncestorsClasses != null) {
      for (int i = reversedAncestorsClasses.length - 1; i >= 0; i--) {
        parent = cur.getParent();
        if (parent == null || !reversedAncestorsClasses[i].isInstance(parent)) {
          return Optional.empty();
        }
        cur = parent;
      }
    }

    parent = cur.getParent();
    if (parent == null || !topmostAncestorClass.isInstance(parent)) {
      return Optional.empty();
    }

    return (Optional<T>)Optional.of(parent);
  }
}
