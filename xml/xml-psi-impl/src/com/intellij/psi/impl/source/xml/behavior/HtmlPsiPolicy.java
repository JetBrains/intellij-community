// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.xml.behavior;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

public class HtmlPsiPolicy extends DefaultXmlPsiPolicy {
  @Override
  protected @NotNull String buildTagForText(PsiElement text, String displayText) {
    if (text instanceof XmlText) {
      XmlTag tag = ((XmlText)text).getParentTag();
      if (tag != null) {
        return "<" + tag.getName() + " " + StringUtil.join(ContainerUtil.map(tag.getAttributes(), PsiElement::getText), " ") + ">" +
               displayText +
               "</" + tag.getName() + ">";
      }
    }
    return super.buildTagForText(text, displayText);
  }
}
