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
import com.intellij.psi.PsiElement;
import com.intellij.psi.XmlElementVisitor;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import org.intellij.lang.xpath.xslt.XsltSupport;
import org.jetbrains.annotations.NotNull;

public class XsltXmlAnnotator extends XmlElementVisitor implements Annotator {

  private AnnotationHolder myHolder;

  public void annotate(@NotNull PsiElement psiElement, @NotNull AnnotationHolder holder) {
    try {
      myHolder = holder;
      psiElement.accept(this);
    } finally {
      myHolder = null;
    }
  }

  @Override
  public void visitXmlAttributeValue(XmlAttributeValue value) {
    final String s = value.getValue();
    if (s == null || s.trim().length() == 0) {
      final PsiElement parent = value.getParent();
      if (parent instanceof XmlAttribute && XsltSupport.isXPathAttribute((XmlAttribute)parent)) {
        myHolder.createErrorAnnotation(value, "Empty XPath expression");
      }
    }
    super.visitXmlAttributeValue(value);
  }
}
