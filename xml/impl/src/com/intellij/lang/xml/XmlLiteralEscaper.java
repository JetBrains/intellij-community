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
package com.intellij.lang.xml;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LiteralEscaper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlToken;

/**
 * @author Gregory.Shrago
 */
public class XmlLiteralEscaper implements LiteralEscaper {
  public String getEscapedText(PsiElement context, String originalText) {
    if (context instanceof XmlToken) {
      context = context.getParent();
    }

    ASTNode contextNode = context != null ? context.getNode():null;
    if (contextNode != null && contextNode.getElementType() == XmlElementType.XML_CDATA) {
      return originalText;
    }
    return escapeText(originalText);
  }

  public String escapeText(String originalText) {
    return StringUtil.escapeXml(originalText);
  }

  public String unescapeText(String originalText) {
    return StringUtil.unescapeXml(originalText);
  }
}
