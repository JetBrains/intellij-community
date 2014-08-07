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

import com.intellij.codeInsight.template.emmet.nodes.GenerationNode;
import com.intellij.codeInsight.template.emmet.tokens.TemplateToken;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.XmlElementVisitor;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagValue;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Eugene.Kudelevsky
 */
public class TrimZenCodingFilter extends ZenCodingFilter {
  private static final Pattern PATTERN = Pattern.compile("^([\\s|\u00a0])?[\\d|#|\\-|\\*|\u2022]+\\.?\\s*");

  @NotNull
  @Override
  public String getSuffix() {
    return "t";
  }

  @Override
  public boolean isMyContext(@NotNull PsiElement context) {
    return context.getLanguage() instanceof XMLLanguage;
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return "Trim line markers";
  }

  @NotNull
  @Override
  public String filterText(@NotNull String text, @NotNull TemplateToken token) {
    XmlDocument document = token.getFile().getDocument();
    if (document != null) {
      XmlTag tag = document.getRootTag();
      if (tag != null && !tag.getText().isEmpty()) {
        tag.accept(new XmlElementVisitor() {
          @Override
          public void visitXmlTag(final XmlTag tag) {
            if (!tag.isEmpty()) {
              final XmlTagValue tagValue = tag.getValue();
              final Matcher matcher = PATTERN.matcher(tagValue.getText());
              if (matcher.matches()) {
                ApplicationManager.getApplication().runWriteAction(new Runnable() {
                  @Override
                  public void run() {
                    tagValue.setText(matcher.replaceAll(""));
                  }
                });
              }
            }
            tag.acceptChildren(this);
          }
        });
        return tag.getText();
      }
      else {
        return PATTERN.matcher(document.getText()).replaceAll("");
      }
    }
    return text;
  }

  @NotNull
  @Override
  public GenerationNode filterNode(@NotNull GenerationNode node) {
    doFilter(node);
    return node;
  }

  private static void doFilter(GenerationNode node) {
    final String surroundedText = node.getSurroundedText();
    if (surroundedText != null) {
      node.setSurroundedText(PATTERN.matcher(surroundedText).replaceAll(""));
    }
    for (GenerationNode child : node.getChildren()) {
      doFilter(child);
    }
  }
}
