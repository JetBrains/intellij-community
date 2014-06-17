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
package com.intellij.xml.util.documentation;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;

/**
 * @author maxim
 */
public class XHtmlDocumentationProvider extends HtmlDocumentationProvider {

  @Override
  protected String generateDocForHtml(PsiElement element, boolean omitHtmlSpecifics, XmlTag context, PsiElement originalElement) {
    return super.generateDocForHtml(element, true, context, originalElement);
  }

  @Override
  protected XmlTag findTagContext(PsiElement context) {
    XmlTag tagBeforeWhiteSpace = findTagBeforeWhiteSpace(context);
    if (tagBeforeWhiteSpace != null) return tagBeforeWhiteSpace;
    return super.findTagContext(context);
  }

  private static XmlTag findTagBeforeWhiteSpace(PsiElement context) {
    if (context instanceof PsiWhiteSpace) {
      PsiElement parent = context.getParent();
      if (parent instanceof XmlText) {
        PsiElement prevSibling = parent.getPrevSibling();
        if (prevSibling instanceof XmlTag) return (XmlTag)prevSibling;
      }
      else if (parent instanceof XmlTag) {
        return (XmlTag)parent;
      }
    }

    return null;
  }

  @Override
  protected boolean isAttributeContext(PsiElement context) {
    if (findTagBeforeWhiteSpace(context) != null) return false;

    return super.isAttributeContext(context);
  }
}
