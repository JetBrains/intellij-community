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
package com.intellij.codeInsight.template.zencoding.filters;

import com.intellij.codeInsight.template.zencoding.tokens.TemplateToken;
import com.intellij.codeInsight.template.zencoding.tokens.XmlTemplateToken;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene.Kudelevsky
 */
public class CommentZenCodingFilter extends ZenCodingFilter {
  private static String buildCommentString(@Nullable String classAttr, @Nullable String idAttr) {
    StringBuilder builder = new StringBuilder();
    if (idAttr != null) {
      builder.append('#').append(idAttr);
    }
    if (classAttr != null) {
      builder.append('.').append(classAttr);
    }
    return builder.toString();
  }

  @NotNull
  @Override
  public String filterText(@NotNull String text, @NotNull TemplateToken token) {
    if (token instanceof XmlTemplateToken) {
      XmlDocument document = ((XmlTemplateToken)token).getFile().getDocument();
      if (document != null) {
        XmlTag tag = document.getRootTag();
        if (tag != null) {
          String classAttr = tag.getAttributeValue("class");
          String idAttr = tag.getAttributeValue("id");
          if (classAttr != null || idAttr != null) {
            String commentString = buildCommentString(classAttr, idAttr);
            return "<!-- " + commentString + " -->\n" + text + "\n<!-- /" + commentString + " -->";
          }
        }
      }
    }
    return text;
  }

  @NotNull
  @Override
  public String getSuffix() {
    return "c";
  }

  @Override
  public boolean isMyContext(@NotNull PsiElement context) {
    return context.getLanguage() instanceof XMLLanguage;
  }
}
