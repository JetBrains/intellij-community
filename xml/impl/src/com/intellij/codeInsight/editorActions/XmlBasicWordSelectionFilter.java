// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlElement;


public class XmlBasicWordSelectionFilter implements Condition<PsiElement> {
  @Override
  public boolean value(final PsiElement e) {
    return !(e instanceof XmlElement);
  }
}
