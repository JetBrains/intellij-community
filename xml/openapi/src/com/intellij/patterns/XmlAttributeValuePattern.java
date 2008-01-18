/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.patterns;

import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.patterns.NullablePatternCondition;
import com.intellij.patterns.MatchingContext;
import com.intellij.patterns.TraverseContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class XmlAttributeValuePattern extends XmlElementPattern<XmlAttributeValue,XmlAttributeValuePattern>{
  protected XmlAttributeValuePattern() {
    super(new NullablePatternCondition() {
      public boolean accepts(@Nullable final Object o,
                                final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
        return o instanceof XmlAttributeValue;
      }
    });
  }

}
