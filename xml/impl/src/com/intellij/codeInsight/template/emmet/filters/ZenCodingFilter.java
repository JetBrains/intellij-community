/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
    Collections.addAll(generators, EP_NAME.getExtensions());
    return generators;
  }
}
