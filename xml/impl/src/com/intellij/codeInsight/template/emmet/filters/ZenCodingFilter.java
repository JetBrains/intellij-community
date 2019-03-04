// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template.emmet.filters;

import com.intellij.application.options.emmet.EmmetOptions;
import com.intellij.codeInsight.template.emmet.nodes.GenerationNode;
import com.intellij.codeInsight.template.emmet.tokens.TemplateToken;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public abstract class ZenCodingFilter {
  public static final ExtensionPointName<ZenCodingFilter> EP_NAME = new ExtensionPointName<>("com.intellij.xml.zenCodingFilter");

  private static final ZenCodingFilter[] OUR_STANDARD_FILTERS = new ZenCodingFilter[]{
    new XslZenCodingFilter(),
    new CommentZenCodingFilter(),
    new EscapeZenCodingFilter(),
    new SingleLineEmmetFilter(),
    new BemEmmetFilter(),
    new TrimZenCodingFilter()
  };

  @NotNull
  public String filterText(@NotNull String text, @NotNull TemplateToken token) {
    return text;
  }

  @NotNull
  public GenerationNode filterNode(@NotNull GenerationNode node) {
    return node;
  }

  @NotNull
  public abstract String getSuffix();

  public abstract boolean isMyContext(@NotNull PsiElement context);

  public boolean isAppliedByDefault(@NotNull PsiElement context) {
    return EmmetOptions.getInstance().isFilterEnabledByDefault(this);
  }

  @NotNull
  public abstract String getDisplayName();

  public static List<ZenCodingFilter> getInstances() {
    List<ZenCodingFilter> generators = new ArrayList<>();
    Collections.addAll(generators, OUR_STANDARD_FILTERS);
    generators.addAll(EP_NAME.getExtensionList());
    return generators;
  }
}
