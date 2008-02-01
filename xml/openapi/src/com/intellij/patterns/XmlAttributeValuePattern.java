/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.patterns;

import com.intellij.psi.xml.*;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

/**
 * @author peter
 */
public class XmlAttributeValuePattern extends XmlElementPattern<XmlAttributeValue,XmlAttributeValuePattern>{
  protected XmlAttributeValuePattern() {
    super(new InitialPatternCondition<XmlAttributeValue>(XmlAttributeValue.class) {
      public boolean accepts(@Nullable final Object o,
                                final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
        return o instanceof XmlAttributeValue;
      }
    });
  }

  public XmlAttributeValuePattern withLocalName(@NonNls String... names) {
    return withLocalName(StandardPatterns.string().oneOf(names));
  }

  public XmlAttributeValuePattern withLocalNameIgnoreCase(@NonNls String... names) {
    return withLocalName(StandardPatterns.string().oneOfIgnoreCase(names));
  }

  public XmlAttributeValuePattern withLocalName(ElementPattern<String> namePattern) {
    return with(new PsiNamePatternCondition<XmlAttributeValue>(namePattern) {
      public String getPropertyValue(@NotNull final Object o) {
        if (o instanceof XmlAttributeValue) {
          final XmlAttributeValue value = (XmlAttributeValue)o;
          final PsiElement parent = value.getParent();
          if (parent instanceof XmlAttribute) {
            return ((XmlAttribute)parent).getLocalName();
          }
          if (parent instanceof XmlProcessingInstruction) {
            PsiElement prev = value.getPrevSibling();
            if (!(prev instanceof XmlToken) || ((XmlToken)prev).getTokenType() != XmlTokenType.XML_EQ) return null;
            prev = prev.getPrevSibling();
            if (!(prev instanceof XmlToken) || ((XmlToken)prev).getTokenType() != XmlTokenType.XML_NAME) return null;
            return prev.getText();
          }
        }
        return null;
      }
    });
  }

}
