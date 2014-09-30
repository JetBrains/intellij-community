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
package com.intellij.codeInsight.folding.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.util.HtmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.StringTokenizer;

/**
 * @author yole
 */
public class XmlElementSignatureProvider extends AbstractElementSignatureProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.folding.impl.XmlElementSignatureProvider");

  @Override
  public String getSignature(@NotNull PsiElement element) {
    if (element instanceof XmlTag) {
      XmlTag tag = (XmlTag)element;
      PsiElement parent = tag.getParent();

      StringBuilder buffer = new StringBuilder();
      buffer.append("tag").append(ELEMENT_TOKENS_SEPARATOR);
      String name = tag.getName();
      buffer.append(name.length() == 0 ? "<unnamed>" : escape(name));

      buffer.append(ELEMENT_TOKENS_SEPARATOR);
      buffer.append(getChildIndex(tag, parent, name, XmlTag.class));

      if (!(parent instanceof PsiFile)) {
        String parentSignature = getSignature(parent);
        if (parentSignature == null) {
          return null;
        }
        buffer.append(";");
        buffer.append(parentSignature);
      }

      return buffer.toString();
    }
    return null;
  }

  @Override
  protected PsiElement restoreBySignatureTokens(@NotNull PsiFile file,
                                                @NotNull PsiElement parent,
                                                @NotNull String type,
                                                @NotNull StringTokenizer tokenizer,
                                                @Nullable StringBuilder processingInfoStorage)
  {
    if (type.equals("tag")) {
      String name = tokenizer.nextToken();

      if (parent instanceof XmlFile) {
        parent = ((XmlFile)parent).getDocument();
        if (parent == null) {
          return null;
        }
      }

      try {
        int index = Integer.parseInt(tokenizer.nextToken());
        String unescapedName = unescape(name);
        PsiElement result = restoreElementInternal(parent, unescapedName, index, XmlTag.class);

        if (result == null &&
            file.getFileType() == StdFileTypes.JSP) {
          //TODO: FoldingBuilder API, psi roots, etc?
          if (parent instanceof XmlDocument) {
            // html tag, not found in jsp tree
            result = restoreElementInternal(HtmlUtil.getRealXmlDocument((XmlDocument)parent), unescapedName, index, XmlTag.class);
          }
          else if (name.equals("<unnamed>") && parent != null) {
            // scriplet/declaration missed because null name
            result = restoreElementInternal(parent, "", index, XmlTag.class);
          }
        }

        return result;
      }
      catch (NumberFormatException e) {
        LOG.error(e);
        return null;
      }
    }
    return null;
  }
}
