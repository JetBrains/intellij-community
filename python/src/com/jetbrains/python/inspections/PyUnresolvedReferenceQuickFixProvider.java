package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiReference;
import com.intellij.util.Consumer;

/**
 * @author yole
 */
public interface PyUnresolvedReferenceQuickFixProvider {
  ExtensionPointName<PyUnresolvedReferenceQuickFixProvider> EP_NAME = ExtensionPointName.create("Pythonid.unresolvedReferenceQuickFixProvider");

  void registerQuickFixes(PsiReference reference, Consumer<LocalQuickFix> fixConsumer);
}
