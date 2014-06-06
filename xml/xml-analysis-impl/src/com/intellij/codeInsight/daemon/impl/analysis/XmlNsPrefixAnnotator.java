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
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Dmitry Avdeev
 *         Date: 25.10.13
 */
public class XmlNsPrefixAnnotator implements Annotator {
  @Override
  public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
    if (element instanceof XmlTag || element instanceof XmlAttribute) {
      List<SchemaPrefixReference> references = ContainerUtil.findAll(element.getReferences(), SchemaPrefixReference.class);
      for (SchemaPrefixReference reference : references) {
        TextRange rangeInElement = reference.getRangeInElement();
        if (!rangeInElement.isEmpty()) {
          TextRange range = rangeInElement.shiftRight(element.getTextRange().getStartOffset());
          holder.createInfoAnnotation(range, null).setTextAttributes(XmlHighlighterColors.XML_NS_PREFIX);
        }
      }
    }
  }
}
