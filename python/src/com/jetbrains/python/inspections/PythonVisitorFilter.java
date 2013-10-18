package com.jetbrains.python.inspections;

import com.intellij.lang.LanguageExtension;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * User : catherine
 *
 * filter for pythonvisitor.
 * check if we should visit element
 */
public interface PythonVisitorFilter {
  LanguageExtension<PythonVisitorFilter> INSTANCE = new LanguageExtension<PythonVisitorFilter>("Pythonid.visitorFilter");

  boolean isSupported(@NotNull Class visitorClass, @NotNull PsiFile file);
}
