/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.patterns;

import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlText;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class XmlElementPattern<T extends XmlElement,Self extends XmlElementPattern<T,Self>> extends PsiElementPattern<T,Self> {
  protected XmlElementPattern(final Class<T> aClass) {
    super(aClass);
  }

  public XmlElementPattern(@NotNull final NullablePatternCondition condition) {
    super(condition);
  }

  public static class Capture extends XmlElementPattern<XmlElement, Capture> {
    protected Capture() {
      super(new NullablePatternCondition() {
        public boolean accepts(@Nullable final Object o,
                                  final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
          return o instanceof XmlElement;
        }
      });
    }
  }

  public static class XmlTextPattern extends XmlElementPattern<XmlText, XmlTextPattern> {
    public XmlTextPattern() {
      super(new NullablePatternCondition() {
        public boolean accepts(@Nullable final Object o,
                                  final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
          return o instanceof XmlText;
        }
      });
    }
  }

}
