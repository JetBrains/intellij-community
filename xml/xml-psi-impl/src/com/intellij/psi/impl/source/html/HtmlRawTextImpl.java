// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.html;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.xml.XmlElement;
import com.intellij.xml.util.XmlPsiUtil;
import org.jetbrains.annotations.NotNull;

public class HtmlRawTextImpl extends ASTWrapperPsiElement implements XmlElement {

  public HtmlRawTextImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public String toString() {
    return "HtmlRawText";
  }

  @Override
  public boolean processElements(PsiElementProcessor processor, PsiElement place) {
    return XmlPsiUtil.processXmlElements(this, processor, false);
  }

  @Override
  public boolean skipValidation() {
    return true;
  }
}
