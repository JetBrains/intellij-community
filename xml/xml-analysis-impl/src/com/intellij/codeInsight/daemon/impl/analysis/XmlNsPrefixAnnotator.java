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
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.editor.XmlHighlighterColors;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.xml.SchemaPrefixReference;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class XmlNsPrefixAnnotator implements Annotator {
  @Override
  public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
    if (PsiUtilCore.getElementType(element) != XmlTokenType.XML_NAME) return;
    PsiElement parent = element.getParent();
    if (!(parent instanceof XmlTag) && !(parent instanceof XmlAttribute)) return;
    TextRange elementRange = element.getTextRange();
    List<SchemaPrefixReference> references = ContainerUtil.findAll(parent.getReferences(), SchemaPrefixReference.class);
    for (SchemaPrefixReference ref : references) {
      TextRange rangeInElement = ref.getRangeInElement();
      if (rangeInElement.isEmpty()) continue;
      TextRange range = rangeInElement.shiftRight(ref.getElement().getTextRange().getStartOffset());
      if (!range.intersects(elementRange)) continue;
      holder.createInfoAnnotation(range, null).setTextAttributes(XmlHighlighterColors.XML_NS_PREFIX);
    }
  }
}
