package com.jetbrains.python.documentation;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface PythonDocumentationLinkProvider {
  ExtensionPointName<PythonDocumentationLinkProvider> EP_NAME = ExtensionPointName.create("Pythonid.documentationLinkProvider");

  @Nullable
  String getExternalDocumentationUrl(PsiElement element, PsiElement originalElement);

  String getExternalDocumentationRoot(String pyVersion);
}
