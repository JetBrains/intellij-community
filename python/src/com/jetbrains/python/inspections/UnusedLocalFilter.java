package com.jetbrains.python.inspections;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;

/**
 * @author yole
 */
public interface UnusedLocalFilter {
  ExtensionPointName<UnusedLocalFilter> EP_NAME = ExtensionPointName.create("Pythonid.unusedLocalFilter");

  boolean ignoreUnused(PsiElement local);
}
