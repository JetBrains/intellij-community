// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.patterns.InitialPatternCondition;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceRegistrar;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.documentation.docstrings.DocStringUtil;
import com.jetbrains.python.patterns.PyElementPattern;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.regex.Pattern;

public class PythonPathReferenceContributor extends PsiReferenceContributor {
  private static final Pattern FILTERING_PATTERN = Pattern.compile("[/\\\\]", SystemInfo.isFileSystemCaseSensitive ? Pattern.CASE_INSENSITIVE : 0);
  private static final int STRING_LITERAL_LIMIT = 10_000;

  /** For this functions every String will be treated as a candidate for path */
  private static final Collection<String> DEFAULT_FUNCTIONS_TO_CHECK = Arrays.asList("read_csv", "open");
  private final Collection<String> functionsNamesToCheck;

  public PythonPathReferenceContributor() {
    // TODO: remove

    //if (checkPredifinedListOfFunctions) {
      functionsNamesToCheck = DEFAULT_FUNCTIONS_TO_CHECK;
    //} else {
    //  functionsNamesToCheck = new String[] {};
    //}
  }

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
      .registerReferenceProvider(matcher(functionsNamesToCheck),
                                 new PythonPathReferenceProvider());
  }

  public static PyElementPattern.Capture<PyStringLiteralExpression> matcher(Collection<String> functionsToCheck) {
    return new PyElementPattern.Capture<>(new InitialPatternCondition<PyStringLiteralExpression>(PyStringLiteralExpression.class) {
      @Override
      public boolean accepts(@Nullable Object o, ProcessingContext context) {
        final PyStringLiteralExpression expr = (PyStringLiteralExpression)o;

        if (checkFunction(functionsToCheck, expr)) {
          return true;
        }

        // We complete only the last string element.
        if (!DocStringUtil.isDocStringExpression(expr) && expr.getTextLength() < STRING_LITERAL_LIMIT) {
          final String value = expr.getStringValue();
          return FILTERING_PATTERN.matcher(value).find();
        }
        return false;
      }
    });
  }

  public static boolean checkFunction(Collection<String> functionsToCheck, PyStringLiteralExpression expr) {
    // Quick return if there are no functions to check.
    if (functionsToCheck.isEmpty()) {
      return false;
    }

    Optional<PyCallExpression> call = getAncestorByBackwardPath(expr, PyCallExpression.class, PyArgumentList.class);

    return call.map(c -> c.getCallee() != null && functionsToCheck.contains(c.getCallee().getName())).orElse(false);
  }

  private static <T extends PsiElement> Optional<T> getAncestorByBackwardPath(PsiElement el,
                                                                              Class<T> topmostAncestor,
                                                                              Class<? extends PsiElement> ... ancestors) {
    PsiElement cur = el;

    PsiElement parent;
    if (ancestors != null) {
      for (int i = ancestors.length - 1; i >= 0; i--) {
        parent = cur.getParent();
        if (parent == null || !ancestors[i].isInstance(parent)) {
          return Optional.empty();
        }
        cur = parent;
      }
    }

    parent = cur.getParent();
    if (parent == null || !topmostAncestor.isInstance(parent)) {
      return Optional.empty();
    }

    return (Optional<T>)Optional.of(parent);
  }
}
