// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.util;

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Utility methods to recognize JSP/JSPX files. 
 * These are to be used in case if there's no direct dependency to JSP modules.
 * <p>
 * In general, if your functionality depends on JSP, consider depending on JSP modules directly.
 */
public final class JspFileTypeUtil {
  private JspFileTypeUtil() { }

  /**
   * @param file file to check
   * @return true if the file is a JSP file
   */
  @Contract(pure = true)
  public static boolean isJsp(@NotNull PsiFile file) {
    return isJsp(file.getFileType());
  }

  /**
   * @param fileType file type to check
   * @return true if the file type is a JSP file type
   */
  @Contract(pure = true)
  public static boolean isJsp(@Nullable FileType fileType) {
    return fileType != null && fileType.getName().equals("JSP");
  }

  /**
   * @param language language to check
   * @return true if the language is a JSP language
   */
  @Contract(pure = true)
  public static boolean isJsp(@Nullable Language language) {
    return language != null && language.getID().equals("JSP");
  }

  /**
   * @param fileType file type to check
   * @return true if the file type is a JSPX file type
   */
  @Contract(pure = true)
  public static boolean isJspX(@Nullable FileType fileType) {
    return fileType != null && fileType.getName().equals("JSPX");
  }

  /**
   * @param language language to check
   * @return true if the language is a JSPX language
   */
  @Contract(pure = true)
  public static boolean isJspX(@Nullable Language language) {
    return language != null && language.getID().equals("JSPX");
  }

  /**
   * @param file file to check
   * @return true if the file is a JSP file or a JSPX file
   */
  @Contract(pure = true)
  public static boolean isJspOrJspX(@NotNull PsiFile file) {
    return isJspOrJspX(file.getFileType());
  }

  /**
   * @param fileType file type to check
   * @return true if the file type is a JSP file type or a JSPX file type
   */
  @Contract(pure = true)
  public static boolean isJspOrJspX(@Nullable FileType fileType) {
    return isJsp(fileType) || isJspX(fileType);
  }

  /**
   * @param language language to check
   * @return true if the language is a JSP language or a JSPX language
   */
  @Contract(pure = true)
  public static boolean isJspOrJspX(@Nullable Language language) {
    return isJsp(language) || isJspX(language);
  }
}
