// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.util.XmlTagUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
public class XmlNamespaceAnnotator implements Annotator {

  private static final XmlNSColorProvider[] PROVIDERS = XmlNSColorProvider.EXTENSION_POINT_NAME.getPoint(null).getExtensions();

  @Override
  public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
    if (element instanceof XmlTag) {
      XmlTag tag = (XmlTag)element;
      String namespace = tag.getNamespace();
      for (XmlNSColorProvider provider : PROVIDERS) {
        TextAttributesKey key = provider.getKeyForNamespace(namespace, tag);
        if (key != null) {
          TextRange range = XmlTagUtil.getStartTagRange(tag);
          if (range != null) {
            holder.createInfoAnnotation(range, null).setTextAttributes(key);
          }
          TextRange endTagRange = XmlTagUtil.getEndTagRange(tag);
          if (endTagRange != null) {
            holder.createInfoAnnotation(endTagRange, null).setTextAttributes(key);
          }
          return;
        }
      }
    }
  }
}
