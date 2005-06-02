package com.intellij.codeInsight.daemon.impl;

import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.psi.PsiFile;

public class HighlightInfoFilterImpl implements HighlightInfoFilter, ApplicationComponent {
  public boolean accept(HighlightInfo info, PsiFile file) {
    TextAttributes attributes = info.getTextAttributes();
    if (attributes == null || attributes.isEmpty() && info.getSeverity() == HighlightSeverity.INFORMATION) return false; // optimization
    return true;
  }

  public String getComponentName() {
    return "HighlightInfoFilter";
  }

  public void initComponent() { }
  public void disposeComponent() { }
}
