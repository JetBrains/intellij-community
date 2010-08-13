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
package com.intellij.codeInsight.template.zencoding.generators;

import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.codeInsight.template.zencoding.tokens.TemplateToken;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.util.HtmlUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;


/**
 * @author Eugene.Kudelevsky
 */
public abstract class XmlZenCodingGenerator extends ZenCodingGenerator {
  @Override
  public TemplateImpl generateTemplate(@NotNull TemplateToken token, boolean hasChildren, @NotNull PsiElement context) {
    String s = toString(token, hasChildren, context);
    TemplateImpl template = token.getTemplate().copy();
    template.setString(s);
    return template;
  }

  @Override
  public TemplateImpl createTemplateByKey(@NotNull String key) {
    StringBuilder builder = new StringBuilder("<");
    builder.append(key).append('>');
    if (!HtmlUtil.isSingleHtmlTag(key)) {
      builder.append("$END$</").append(key).append('>');
    }
    return new TemplateImpl("", builder.toString(), "");
  }

  @NotNull
  private String toString(@NotNull TemplateToken token, boolean hasChildren, @NotNull PsiElement context) {
    XmlFile file = token.getFile();
    XmlDocument document = file.getDocument();
    if (document != null) {
      XmlTag tag = document.getRootTag();
      if (tag != null) {
        return toString(tag, token.getAttribute2Value(), hasChildren, context);
      }
    }
    return file.getText();
  }

  public abstract String toString(@NotNull XmlTag tag,
                                  @NotNull List<Pair<String, String>> attribute2Value,
                                  boolean hasChildren,
                                  @NotNull PsiElement context);

  @NotNull
  public abstract String buildAttributesString(@NotNull List<Pair<String, String>> attribute2value,
                                               boolean hasChildren,
                                               int numberInIteration);

  public abstract boolean isMyContext(@NotNull PsiElement context);
}
