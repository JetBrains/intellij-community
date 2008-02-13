/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.patterns;

import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public abstract class XmlNamedElementPattern<T extends XmlElement & PsiNamedElement,Self extends XmlNamedElementPattern<T,Self>> extends XmlElementPattern<T,Self>{

  public XmlNamedElementPattern(@NotNull final InitialPatternCondition<T> condition) {
    super(condition);
  }

  protected abstract String getLocalName(T t);
  protected abstract String getNamespace(T t);

  public Self withLocalName(@NonNls String localName) {
    return withLocalName(StandardPatterns.string().equalTo(localName));
  }

  public Self withLocalName(final ElementPattern<String> localName) {
    return with(new PsiNamePatternCondition<T>("withLocalName", localName) {
      public String getPropertyValue(@NotNull final Object o) {
        return o instanceof XmlElement ? getLocalName((T)o) : null;
      }
    });
  }

  public Self withNamespace(final String... namespaces) {
    return withNamespace(PlatformPatterns.string().oneOf(namespaces));
  }

  public Self withNamespace(final ElementPattern<String> namespace) {
    return with(new PatternCondition<T>("withNamespace") {
      public boolean accepts(@NotNull final T s, final ProcessingContext context) {
        return namespace.accepts(getNamespace(s));
      }
    });
  }

  public static class XmlAttributePattern extends XmlNamedElementPattern<XmlAttribute, XmlAttributePattern> {
    protected XmlAttributePattern() {
      super(new InitialPatternCondition<XmlAttribute>(XmlAttribute.class) {
        public boolean accepts(@Nullable final Object o, final ProcessingContext context) {
          return o instanceof XmlAttribute;
        }
      });
    }

    protected String getLocalName(XmlAttribute xmlAttribute) {
      return xmlAttribute.getLocalName();
    }

    protected String getNamespace(XmlAttribute xmlAttribute) {
      return xmlAttribute.getNamespace();
    }

  }

}
