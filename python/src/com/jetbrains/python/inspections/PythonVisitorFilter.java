package com.jetbrains.python.inspections;

import com.intellij.lang.LanguageExtension;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * User : catherine
 *
 * filter for pythonvisitor.
 * check if we should visit element
 */
public interface PythonVisitorFilter {
  public static final LanguageExtension<PythonVisitorFilter> INSTANCE =
    new LanguageExtension<PythonVisitorFilter>("Pythonid.visitorFilter");

  boolean isSupported(@NotNull Class visitorClass, @NotNull PsiElement element);
}
