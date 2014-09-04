package com.jetbrains.python.documentation;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Allows you to inject quick info into python documentation provider
 *
 * @author Ilya.Kazakevich
 */
public interface PythonDocumentationQuickInfoProvider {
  ExtensionPointName<PythonDocumentationQuickInfoProvider> EP_NAME =
    ExtensionPointName.create("Pythonid.pythonDocumentationQuickInfoProvider");

  /**
   * Return quick info for <strong>original</strong> element.
   *
   * @param originalElement original element
   * @return info (if exists) or null (if another provider should be checked)
   */
  @Nullable
  String getQuickInfo(@NotNull PsiElement originalElement);
}
