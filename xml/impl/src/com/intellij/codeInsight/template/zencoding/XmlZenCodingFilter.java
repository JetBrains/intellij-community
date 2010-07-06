/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.codeInsight.template.zencoding;

import com.intellij.codeInsight.template.zencoding.tokens.TemplateToken;
import com.intellij.codeInsight.template.zencoding.tokens.XmlTemplateToken;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public abstract class XmlZenCodingFilter implements ZenCodingFilter {
  @NotNull
  public String toString(@NotNull TemplateToken token, @NotNull PsiElement context) {
    if (!(token instanceof XmlTemplateToken)) {
      throw new IllegalArgumentException();
    }
    return toString(((XmlTemplateToken)token).getTag(), context);
  }

  public abstract String toString(@NotNull XmlTag tag, @NotNull PsiElement context);

  @NotNull
  public abstract String buildAttributesString(@NotNull List<Pair<String, String>> attribute2value, int numberInIteration);

  public abstract boolean isMyContext(@NotNull PsiElement context);
}
