// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.emmet.filters;

import com.intellij.application.options.emmet.EmmetOptions;
import com.intellij.codeInsight.template.emmet.nodes.GenerationNode;
import com.intellij.codeInsight.template.emmet.tokens.TemplateToken;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class ZenCodingFilter {
  private static final ExtensionPointName<ZenCodingFilter> EP_NAME = new ExtensionPointName<>("com.intellij.xml.zenCodingFilter");

  private static class Holder {
    private static final ZenCodingFilter[] OUR_STANDARD_FILTERS = new ZenCodingFilter[]{
      new XslZenCodingFilter(),
      new CommentZenCodingFilter(),
      new EscapeZenCodingFilter(),
      new SingleLineEmmetFilter(),
      new BemEmmetFilter(),
      new TrimZenCodingFilter()
    };
  }

  public @NotNull String filterText(@NotNull String text, @NotNull TemplateToken token) {
    return text;
  }

  public @NotNull GenerationNode filterNode(@NotNull GenerationNode node) {
    return node;
  }

  public abstract @NotNull String getSuffix();

  public abstract boolean isMyContext(@NotNull PsiElement context);

  public boolean isAppliedByDefault(@NotNull PsiElement context) {
    return isSystem() || EmmetOptions.getInstance().isFilterEnabledByDefault(this);
  }

  /**
   * @return true if the filter shouldn't be shown in the emmet-related UI. Also, such filters are always enabled by default
   */
  public boolean isSystem() {
    return false;
  }

  public abstract @NotNull @NlsContexts.Label String getDisplayName();

  public static List<ZenCodingFilter> getInstances() {
    List<ZenCodingFilter> generators = new ArrayList<>();
    Collections.addAll(generators, Holder.OUR_STANDARD_FILTERS);
    generators.addAll(EP_NAME.getExtensionList());
    return generators;
  }
}
