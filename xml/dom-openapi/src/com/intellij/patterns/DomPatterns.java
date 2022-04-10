// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
 * Provides patterns for the DOM-API and builds upon {@link XmlPatterns}.
 * <p>
 * Please see the <a href="https://plugins.jetbrains.com/docs/intellij/element-patterns.html">IntelliJ Platform Docs</a>
 * for a high-level overview.
 *
 * @see XmlPatterns
 */
public class DomPatterns {
  public static <T extends DomElement> DomElementPattern.Capture<T> domElement(Class<T> aClass) {
    return new DomElementPattern.Capture<>(aClass);
  }

  public static DomElementPattern.Capture<DomElement> domElement() {
    return domElement(DomElement.class);
  }

  public static GenericDomValuePattern<?> genericDomValue() {
    return new GenericDomValuePattern<>();
  }

  public static <T> GenericDomValuePattern<T> genericDomValue(ElementPattern<?> valuePattern) {
    @SuppressWarnings("unchecked") GenericDomValuePattern<T> pattern = (GenericDomValuePattern<T>)genericDomValue();
    return pattern.withValue(valuePattern);
  }

  public static <T> GenericDomValuePattern<T> genericDomValue(Class<T> aClass) {
    return new GenericDomValuePattern<>(aClass);
  }

  /** @deprecated use {@link #tagWithDom(String, ElementPattern)} and {@link #attributeWithDom(String, ElementPattern)} */
  @Deprecated(forRemoval = true)
  @SuppressWarnings("DeprecatedIsStillUsed")
  public static XmlElementPattern.Capture withDom(ElementPattern<? extends DomElement> pattern) {
    return new XmlElementPattern.Capture().with(new PatternCondition<>("tagWithDom") {
      @Override
      public boolean accepts(@NotNull XmlElement xmlElement, ProcessingContext context) {
        DomManager manager = DomManager.getDomManager(xmlElement.getProject());
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

  public static XmlTagPattern.Capture tagWithDom(String[] tagNames, ElementPattern<? extends DomElement> domPattern) {
    return XmlPatterns.xmlTag().withLocalName(tagNames).and(withDom(domPattern));
  }

  public static XmlNamedElementPattern.XmlAttributePattern attributeWithDom(String attributeName, ElementPattern<? extends DomElement> domPattern) {
    return XmlPatterns.xmlAttribute().withLocalName(attributeName).and(withDom(domPattern));
  }

  public static PsiElementPattern.Capture<PomTargetPsiElement> domTargetElement(ElementPattern<? extends DomElement> pattern) {
    return PlatformPatterns.pomElement(withDomTarget(pattern));
  }

  public static ElementPattern<DomTarget> withDomTarget(ElementPattern<? extends DomElement> pattern) {
    return new ObjectPattern.Capture<>(DomTarget.class).with(new PatternCondition<>("withDomTarget") {
      @Override
      public boolean accepts(@NotNull DomTarget target, ProcessingContext context) {
        return pattern.accepts(target.getDomElement(), context);
      }
    });
  }
}
