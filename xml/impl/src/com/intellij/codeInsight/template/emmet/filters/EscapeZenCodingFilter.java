// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.emmet.filters;

import com.intellij.codeInsight.template.emmet.tokens.TemplateToken;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.psi.PsiElement;
import com.intellij.xml.XmlBundle;
import org.jetbrains.annotations.NotNull;

public class EscapeZenCodingFilter extends ZenCodingFilter {
  @Override
  public @NotNull String filterText(@NotNull String s, @NotNull TemplateToken token) {
    s = s.replace("&", "&amp;");
    s = s.replace("<", "&lt;");
    s = s.replace(">", "&gt;");
    return s;
  }

  @Override
  public @NotNull String getSuffix() {
    return "e";
  }

  @Override
  public boolean isMyContext(@NotNull PsiElement context) {
    return context.getLanguage() instanceof XMLLanguage;
  }

  @Override
  public @NotNull String getDisplayName() {
    return XmlBundle.message("emmet.filter.escape");
  }
}
