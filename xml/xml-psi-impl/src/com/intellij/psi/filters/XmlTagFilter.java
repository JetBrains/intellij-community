// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author max
 */
package com.intellij.psi.filters;

import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlTag;

public final class XmlTagFilter implements ElementFilter {
  public static final XmlTagFilter INSTANCE = new XmlTagFilter();

  private XmlTagFilter() {}

  @Override
  public boolean isAcceptable(Object element, PsiElement context) {
    return element instanceof XmlTag;
  }

  @Override
  public boolean isClassAcceptable(Class hintClass) {
    return true;
  }
}
