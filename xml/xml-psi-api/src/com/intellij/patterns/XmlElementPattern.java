// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.patterns;

import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlEntityRef;
import com.intellij.psi.xml.XmlText;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class XmlElementPattern<T extends XmlElement,Self extends XmlElementPattern<T,Self>> extends PsiElementPattern<T,Self> {
  protected XmlElementPattern(final Class<T> aClass) {
    super(aClass);
  }

  XmlElementPattern(final @NotNull InitialPatternCondition<T> condition) {
    super(condition);
  }

  public static class Capture extends XmlElementPattern<XmlElement, Capture> {
    Capture() {
      super(new InitialPatternCondition<>(XmlElement.class) {
        @Override
        public boolean accepts(final @Nullable Object o, final ProcessingContext context) {
          return o instanceof XmlElement;
        }
      });
    }
  }

  public static class XmlTextPattern extends XmlElementPattern<XmlText, XmlTextPattern> {
    XmlTextPattern() {
      super(new InitialPatternCondition<>(XmlText.class) {
        @Override
        public boolean accepts(final @Nullable Object o, final ProcessingContext context) {
          return o instanceof XmlText;
        }
      });
    }
  }

  public static class XmlEntityRefPattern extends XmlElementPattern<XmlEntityRef, XmlEntityRefPattern> {
    XmlEntityRefPattern() {
      super(new InitialPatternCondition<>(XmlEntityRef.class) {
        @Override
        public boolean accepts(final @Nullable Object o, final ProcessingContext context) {
          return o instanceof XmlEntityRef;
        }
      });
    }
  }

}
