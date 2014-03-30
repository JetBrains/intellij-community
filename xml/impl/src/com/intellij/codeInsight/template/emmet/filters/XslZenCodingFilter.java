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
package com.intellij.codeInsight.template.emmet.filters;

import com.intellij.codeInsight.template.XslTextContextType;
import com.intellij.codeInsight.template.emmet.generators.XmlZenCodingGenerator;
import com.intellij.codeInsight.template.emmet.generators.XmlZenCodingGeneratorImpl;
import com.intellij.codeInsight.template.emmet.nodes.GenerationNode;
import com.intellij.codeInsight.template.emmet.tokens.TemplateToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public class XslZenCodingFilter extends ZenCodingFilter {
  private final XmlZenCodingGenerator myDelegate = new XmlZenCodingGeneratorImpl();
  @NonNls private static final String SELECT_ATTR_NAME = "select";

  @NotNull
  @Override
  public GenerationNode filterNode(@NotNull final GenerationNode node) {
    TemplateToken token = node.getTemplateToken();
    if (token != null) {
      XmlDocument document = token.getFile().getDocument();
      if (document != null) {
        final XmlTag tag = document.getRootTag();
        if (tag != null) {
          for (Pair<String, String> pair : token.getAttribute2Value()) {
            if (SELECT_ATTR_NAME.equals(pair.first)) {
              return node;
            }
          }
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
              if (isOurTag(tag, node.getChildren().size() > 0)) {
                XmlAttribute attribute = tag.getAttribute(SELECT_ATTR_NAME);
                if (attribute != null) {
                  attribute.delete();
                }
              }
            }
          });
          return node;
        }
      }
    }
    return node;
  }

  private static boolean isOurTag(XmlTag tag, boolean hasChildren) {
    if (hasChildren) {
      String name = tag.getLocalName();
      return name.equals("with-param") || name.equals("variable");
    }
    return false;
  }

  @Override
  public boolean isMyContext(@NotNull PsiElement context) {
    return myDelegate.isMyContext(context, true) ||
           myDelegate.isMyContext(context, false);
  }

  @NotNull
  @Override
  public String getSuffix() {
    return "xsl";
  }

  @Override
  public boolean isAppliedByDefault(@NotNull PsiElement context) {
    return XslTextContextType.isXslOrXsltFile(context.getContainingFile());
  }
}
