package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.psi.PsiFile;

public class HighlightInfoFilterImpl implements HighlightInfoFilter, ApplicationComponent {
  public boolean accept(HighlightInfoType type, PsiFile file) {
    TextAttributes attributes = HighlightInfo.getAttributesByType(type);
    if (attributes == null || attributes.isEmpty() && type.getSeverity() == HighlightInfo.INFORMATION) return false; // optimization
    return true;
  }

  public String getComponentName() {
    return "HighlightInfoFilter";
  }

  public void initComponent() { }
  public void disposeComponent() { }
}
