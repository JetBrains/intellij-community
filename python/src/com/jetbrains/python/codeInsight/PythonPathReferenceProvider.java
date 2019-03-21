// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

public class PythonPathReferenceProvider extends PsiReferenceProvider {
  public static final String FILE_SCHEME = "file://";
  // Cache to not count every time.
  public static final int FILE_SCHEME_LENGTH = FILE_SCHEME.length();
  public static final Pattern WIN_DRIVE_LETTER_PATTERN = Pattern.compile("^([a-z]:((/)|(\\\\)))", Pattern.CASE_INSENSITIVE);

  @NotNull
  @Override
  public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
    String text = ((PyStringLiteralExpression)element).getStringValue();

    //if (text.startsWith(FILE_SCHEME)) {
    //  if (text.length() > FILE_SCHEME_LENGTH) {
    //    text = text.substring(FILE_SCHEME_LENGTH);
    //  } else {
    //    text = "";
    //  }
    //}

    String fsRootToCheck = getFsRoot(text);
    return new PythonStringFileReferenceSet(text,
                                            element,
                                            0,
                                            fsRootToCheck).getAllReferences();
  }

  /**
   * In case of a valid absolute path is passed returns file system root in format "file://[fsRoot]".
   * If invalid or relative path is passed, null is returned.
   *
   * @param pathCandidate Candidate for path.
   * @return File system root in case of valid absolute path, null otherwise.
   */
  private String getFsRoot(String pathCandidate) {
    // 1. Remove file scheme.
    String withoutScheme = pathCandidate;
    if (pathCandidate.startsWith(FILE_SCHEME)) {
      if (pathCandidate.length() > FILE_SCHEME_LENGTH) {
        withoutScheme = pathCandidate.substring(FILE_SCHEME_LENGTH);
      } else {
        return null;
      }
    }

    // 2. Quickly return if path is obviously relative.
    if (withoutScheme.startsWith(".")) {
      return null;
    }

    if (withoutScheme.startsWith("/")) {
      return FILE_SCHEME + "/";
    }

    // Drive letters.
    if (SystemInfo.isWindows && WIN_DRIVE_LETTER_PATTERN.matcher(withoutScheme).matches()) {
      return FILE_SCHEME + withoutScheme.substring(0, 3);
    }

    return null;
  }
}
