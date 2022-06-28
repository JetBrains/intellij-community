// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml;

import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.pom.PomTarget;
import com.intellij.pom.PomTargetPsiElement;
import com.intellij.psi.DelegatePsiTarget;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nls;

final class DomDocumentationProvider implements DocumentationProvider {
  @Override
  public @Nls String generateDoc(PsiElement element, final PsiElement originalElement) {
    if (element instanceof PomTargetPsiElement) {
      PomTarget target = ((PomTargetPsiElement)element).getTarget();
      if (target instanceof DelegatePsiTarget) {
        element = ((DelegatePsiTarget)target).getNavigationElement();
      }
    }
    final DomElement domElement = DomUtil.getDomElement(element);
    if (domElement == null) {
      return null;
    }
    ElementPresentationTemplate template = domElement.getChildDescription().getPresentationTemplate();
    if (template != null) {
      String documentation = template.createPresentation(domElement).getDocumentation();
      if (documentation != null) return documentation;
    }
    return null;
  }
}
