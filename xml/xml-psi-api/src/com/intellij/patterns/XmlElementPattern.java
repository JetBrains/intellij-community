// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.patterns;

import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlEntityRef;
import com.intellij.psi.xml.XmlText;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class XmlElementPattern<T extends XmlElement,Self extends XmlElementPattern<T,Self>> extends PsiElementPattern<T,Self> {
  protected XmlElementPattern(final Class<T> aClass) {
    super(aClass);
  }

  XmlElementPattern(final @NotNull InitialPatternCondition<T> condition) {
    super(condition);
  }

  public static final class Capture extends XmlElementPattern<XmlElement, Capture> {
    @ApiStatus.Internal
    public Capture() {
      super(new InitialPatternCondition<>(XmlElement.class) {
        @Override
        public boolean accepts(final @Nullable Object o, final ProcessingContext context) {
          return o instanceof XmlElement;
        }
      });
    }
  }

  public static final class XmlTextPattern extends XmlElementPattern<XmlText, XmlTextPattern> {
    XmlTextPattern() {
      super(new InitialPatternCondition<>(XmlText.class) {
        @Override
        public boolean accepts(final @Nullable Object o, final ProcessingContext context) {
          return o instanceof XmlText;
        }
      });
    }
  }

  public static final class XmlEntityRefPattern extends XmlElementPattern<XmlEntityRef, XmlEntityRefPattern> {
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
