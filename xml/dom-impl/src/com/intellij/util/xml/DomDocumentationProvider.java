// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xml;

import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.pom.PomTarget;
import com.intellij.pom.PomTargetPsiElement;
import com.intellij.psi.DelegatePsiTarget;
import com.intellij.psi.PsiElement;

/**
 * @author Dmitry Avdeev
 */
public class DomDocumentationProvider implements DocumentationProvider {

  @Override
  public String generateDoc(PsiElement element, final PsiElement originalElement) {
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
