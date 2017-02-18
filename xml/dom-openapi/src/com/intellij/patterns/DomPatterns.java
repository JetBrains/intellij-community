/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.patterns;

import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ProcessingContext;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.DomTarget;
import com.intellij.pom.PomTargetPsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class DomPatterns {

  public static <T extends DomElement> DomElementPattern.Capture<T> domElement(Class<T> aClass) {
    return new DomElementPattern.Capture<>(aClass);
  }

  public static DomElementPattern.Capture<DomElement> domElement() {
    return domElement(DomElement.class);
  }

  public static GenericDomValuePattern<?> genericDomValue() {
    return new GenericDomValuePattern();
  }

  public static <T> GenericDomValuePattern<T> genericDomValue(ElementPattern<?> valuePattern) {
    return ((GenericDomValuePattern)genericDomValue()).withValue(valuePattern);
  }

  public static <T> GenericDomValuePattern<T> genericDomValue(Class<T> aClass) {
    return new GenericDomValuePattern<>(aClass);
  }

  /**
   * @deprecated use {@link #tagWithDom(String, ElementPattern)} and  {@link #attributeWithDom(String, ElementPattern)}
   */
  public static XmlElementPattern.Capture withDom(final ElementPattern<? extends DomElement> pattern) {
    return new XmlElementPattern.Capture().with(new PatternCondition<XmlElement>("tagWithDom") {
      @Override
      public boolean accepts(@NotNull final XmlElement xmlElement, final ProcessingContext context) {
        final DomManager manager = DomManager.getDomManager(xmlElement.getProject());
        if (xmlElement instanceof XmlAttribute) {
          return pattern.accepts(manager.getDomElement((XmlAttribute)xmlElement), context);
        }
        return xmlElement instanceof XmlTag && pattern.accepts(manager.getDomElement((XmlTag)xmlElement), context);
      }
    });
  }

  public static <T extends DomElement> DomFilePattern.Capture inDomFile(Class<T> rootElementClass) {
    return new DomFilePattern.Capture(rootElementClass);
  }

  public static XmlTagPattern.Capture tagWithDom(String tagName, Class<? extends DomElement> aClass) {
    return tagWithDom(tagName, domElement(aClass));
  }

  public static XmlTagPattern.Capture tagWithDom(String tagName, ElementPattern<? extends DomElement> domPattern) {
    return XmlPatterns.xmlTag().withLocalName(tagName).and(withDom(domPattern));
  }

  public static XmlNamedElementPattern.XmlAttributePattern attributeWithDom(String attributeName, ElementPattern<? extends DomElement> domPattern) {
    return XmlPatterns.xmlAttribute().withLocalName(attributeName).and(withDom(domPattern));
  }

  public static PsiElementPattern.Capture<PomTargetPsiElement> domTargetElement(final ElementPattern<? extends DomElement> pattern) {
    return PlatformPatterns.pomElement(withDomTarget(pattern));
  }

  public static ElementPattern<DomTarget> withDomTarget(final ElementPattern<? extends DomElement> pattern) {
    return new ObjectPattern.Capture<>(DomTarget.class).with(new PatternCondition<DomTarget>("withDomTarget") {
      @Override
      public boolean accepts(@NotNull final DomTarget target, final ProcessingContext context) {
        return pattern.accepts(target.getDomElement(), context);
      }
    });
  }


}
