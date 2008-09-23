package org.jetbrains.idea.maven.dom.intentions;

import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider;
import com.intellij.codeInsight.daemon.QuickFixActionRegistrar;
import com.intellij.psi.PsiJavaCodeReferenceElement;

public class ResolveReferenceQuickFixProvider implements UnresolvedReferenceQuickFixProvider{
  public void registerFixes(PsiJavaCodeReferenceElement ref, QuickFixActionRegistrar registrar) {
    registrar.register(new DependencyQuickFix(ref));
  }
}
