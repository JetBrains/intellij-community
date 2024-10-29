// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.patterns;

import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.*;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class XmlAttributeValuePattern extends XmlElementPattern<XmlAttributeValue,XmlAttributeValuePattern>{
  static final XmlAttributeValuePattern XML_ATTRIBUTE_VALUE_PATTERN = new XmlAttributeValuePattern(
    new InitialPatternCondition<>(XmlAttributeValue.class) {
      @Override
      public boolean accepts(final @Nullable Object o, final ProcessingContext context) {
        return o instanceof XmlAttributeValue;
      }
    });

  public XmlAttributeValuePattern(InitialPatternCondition<XmlAttributeValue> condition) {
    super(condition);
  }

  public XmlAttributeValuePattern withLocalName(@NonNls String... names) {
    return names.length == 1
           ? withLocalName(StandardPatterns.string().equalTo(names[0]))
           : withLocalName(StandardPatterns.string().oneOf(names));
  }

  public XmlAttributeValuePattern withLocalNameIgnoreCase(@NonNls String... names) {
    return withLocalName(StandardPatterns.string().oneOfIgnoreCase(names));
  }


  public XmlAttributeValuePattern withLocalName(ElementPattern<String> namePattern) {
    return with(new PsiNamePatternCondition<>("withLocalName", namePattern) {
      @Override
      public String getPropertyValue(final @NotNull Object o) {
        if (o instanceof XmlAttributeValue) {
          return getLocalName((XmlAttributeValue)o);
        }
        return null;
      }
    });
  }

  public XmlAttributeValuePattern withNamespace(ElementPattern<String> namePattern) {
    return with(new PsiNamePatternCondition<>("withNamespace", namePattern) {
      @Override
      public String getPropertyValue(final @NotNull Object o) {
        if (o instanceof XmlAttributeValue) {
          final PsiElement parent = ((XmlAttributeValue)o).getParent();
          if (parent instanceof XmlAttribute) {
            return ((XmlAttribute)parent).getNamespace();
          }
        }
        return null;
      }
    });
  }

  public XmlAttributeValuePattern withNamespace(@NonNls String... names) {
    return names.length == 1
           ? withNamespace(StandardPatterns.string().equalTo(names[0]))
           : withNamespace(StandardPatterns.string().oneOf(names));
  }

  public static @Nullable String getLocalName(@NotNull XmlAttributeValue value) {
    final PsiElement parent = value.getParent();
    if (parent instanceof XmlAttribute) {
      return ((XmlAttribute)parent).getLocalName();
    }
    else if (parent instanceof XmlProcessingInstruction) {
      PsiElement prev = value.getPrevSibling();
      if (!(prev instanceof XmlToken) || ((XmlToken)prev).getTokenType() != XmlTokenType.XML_EQ) return null;
      prev = prev.getPrevSibling();
      if (!(prev instanceof XmlToken) || ((XmlToken)prev).getTokenType() != XmlTokenType.XML_NAME) return null;
      return prev.getText();
    }
    else {
      return null;
    }
  }

  public XmlAttributeValuePattern withValue(final StringPattern valuePattern) {
    return with(new PatternCondition<>("withValue") {
      @Override
      public boolean accepts(@NotNull XmlAttributeValue xmlAttributeValue, ProcessingContext context) {
        return valuePattern.accepts(xmlAttributeValue.getValue(), context);
      }
    });
  }

}
