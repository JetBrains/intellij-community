package com.jetbrains.python.codeInsight.imports;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiReference;

/**
 * @author yole
 */
public interface PyImportCandidateProvider {
  ExtensionPointName<PyImportCandidateProvider> EP_NAME = ExtensionPointName.create("Pythonid.importCandidateProvider");

  void addImportCandidates(PsiReference reference, String name, AutoImportQuickFix quickFix);
}
