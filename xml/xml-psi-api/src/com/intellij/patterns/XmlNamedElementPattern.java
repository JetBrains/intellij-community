// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.patterns;

import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.PairProcessor;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class XmlNamedElementPattern<T extends XmlElement & PsiNamedElement,Self extends XmlNamedElementPattern<T,Self>> extends XmlElementPattern<T,Self>{

  public XmlNamedElementPattern(final @NotNull InitialPatternCondition<T> condition) {
    super(condition);
  }

  protected abstract String getLocalName(T t);
  protected abstract String getNamespace(T t);

  public Self withLocalName(@NonNls String localName) {
    return withLocalName(StandardPatterns.string().equalTo(localName));
  }

  public Self withLocalName(@NonNls String... localNames) {
    return withLocalName(StandardPatterns.string().oneOf(localNames));
  }

  public Self withLocalName(final ElementPattern<String> localName) {
    return with(new PsiNamePatternCondition<>("withLocalName", localName) {
      @Override
      public String getPropertyValue(final @NotNull Object o) {
        return o instanceof XmlElement ? getLocalName((T)o) : null;
      }
    });
  }

  public Self withNamespace(final @NonNls String namespace) {
    return withNamespace(StandardPatterns.string().equalTo(namespace));
  }

  public Self withNamespace(final @NonNls String... namespaces) {
    return withNamespace(StandardPatterns.string().oneOf(namespaces));
  }

  public Self withNamespace(final ElementPattern<String> namespace) {
    return with(new PatternConditionPlus<T, String>("withNamespace", namespace) {
      @Override
      public boolean processValues(T t,
                                   ProcessingContext context,
                                   PairProcessor<? super String, ? super ProcessingContext> processor) {
        return processor.process(getNamespace(t), context);
      }
    });
  }

  public static class XmlAttributePattern extends XmlNamedElementPattern<XmlAttribute, XmlAttributePattern> {
    protected XmlAttributePattern() {
      super(new InitialPatternCondition<>(XmlAttribute.class) {
        @Override
        public boolean accepts(final @Nullable Object o, final ProcessingContext context) {
          return o instanceof XmlAttribute;
        }
      });
    }

    @Override
    protected String getLocalName(XmlAttribute xmlAttribute) {
      return xmlAttribute.getLocalName();
    }

    @Override
    protected String getNamespace(XmlAttribute xmlAttribute) {
      return xmlAttribute.getNamespace();
    }
    
    public XmlAttributePattern withValue(final StringPattern pattern) {
      return with(new PatternConditionPlus<XmlAttribute, String>("withValue", pattern) {
        @Override
        public boolean processValues(XmlAttribute t,
                                     ProcessingContext context,
                                     PairProcessor<? super String, ? super ProcessingContext> processor) {
          return processor.process(t.getValue(), context);
        }
      });
    }

  }

}
