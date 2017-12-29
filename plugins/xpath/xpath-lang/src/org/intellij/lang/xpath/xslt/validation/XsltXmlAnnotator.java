/*
 * Copyright 2006 Sascha Weinreuter
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
package org.intellij.lang.xpath.xslt.validation;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.XmlElementVisitor;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.SmartList;
import org.intellij.lang.xpath.XPathFile;
import org.intellij.lang.xpath.xslt.XsltSupport;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class XsltXmlAnnotator extends XmlElementVisitor implements Annotator {

  private AnnotationHolder myHolder;

  @Override
  public void annotate(@NotNull PsiElement psiElement, @NotNull AnnotationHolder holder) {
    try {
      myHolder = holder;
      psiElement.accept(this);
    } finally {
      myHolder = null;
    }
  }

  @Override
  public void visitXmlAttributeValue(final XmlAttributeValue value) {
    final PsiElement parent = value.getParent();
    if (parent instanceof XmlAttribute) {
      if (!XsltSupport.isXsltFile(parent.getContainingFile())) {
        return;
      }

      final String s = value.getValue();

      if (s == null || s.isEmpty()) {
        if (XsltSupport.isXPathAttribute((XmlAttribute)parent)) {
          InjectedLanguageManager.getInstance(value.getProject()).enumerate(value, (injectedPsi, places) -> {
            if (injectedPsi instanceof XPathFile) {
              if (injectedPsi.getTextLength() == 0) {
                myHolder.createErrorAnnotation(value, "Empty XPath expression");
              }
            }
          });
        }
      } else if (XsltSupport.mayBeAVT((XmlAttribute)parent)) {
        final List<Integer> singleBraces = collectClosingBraceOffsets(s);

        if (singleBraces != null) {
          InjectedLanguageManager.getInstance(value.getProject()).enumerate(value, (injectedPsi, places) -> {
            if (injectedPsi instanceof XPathFile) {
              for (PsiLanguageInjectionHost.Shred place : places) {
                final TextRange range = place.getRangeInsideHost();

                singleBraces.removeIf(range::contains);
              }
            }
          });

          for (Integer brace : singleBraces) {
            myHolder.createErrorAnnotation(TextRange.from(value.getTextOffset() + brace, 1), "Invalid single closing brace. Escape as '}}'");
          }
        }
      }
    }
    super.visitXmlAttributeValue(value);
  }

  private static List<Integer> collectClosingBraceOffsets(String s) {
    List<Integer> singleBraces = null;
    int i = -1;
    while ((i = getAVTEndOffset(s, i)) != -1) {
      if (singleBraces == null) {
        singleBraces = new SmartList<>();
      }
      if (i == 0 || s.charAt(i - 1) != '{') {
        singleBraces.add(i);
      }
    }
    return singleBraces;
  }

  private static int getAVTEndOffset(String value, int i) {
    do {
      i = value.indexOf('}', i + 1);
      if (i != -1 && i == value.indexOf("}}", i)) {
        i += 2;
      }
      else {
        break;
      }
    }
    while (i != -1);
    return i;
  }
}
