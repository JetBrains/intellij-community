// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.patterns;

import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

public class XmlTagPattern<Self extends XmlTagPattern<Self>> extends XmlNamedElementPattern<XmlTag, Self> {
  protected XmlTagPattern() {
    super(new InitialPatternCondition<>(XmlTag.class) {
      @Override
      public boolean accepts(final @Nullable Object o, final ProcessingContext context) {
        return o instanceof XmlTag;
      }
    });
  }

  protected XmlTagPattern(final @NotNull InitialPatternCondition<XmlTag> condition) {
    super(condition);
  }

  @Override
  protected String getLocalName(XmlTag tag) {
    return tag.getLocalName();
  }

  @Override
  protected String getNamespace(XmlTag tag) {
    return tag.getNamespace();
  }

  public Self withAttributeValue(final @NotNull @NonNls String attributeName, final @NotNull String attributeValue) {
    return with(new PatternCondition<>("withAttributeValue") {
      @Override
      public boolean accepts(final @NotNull XmlTag xmlTag, final ProcessingContext context) {
        return Objects.equals(xmlTag.getAttributeValue(attributeName), attributeValue);
      }
    });
  }

  public Self withAnyAttribute(final @NonNls String @NotNull ... attributeNames) {
    return with(new PatternCondition<>("withAnyAttribute") {
      @Override
      public boolean accepts(final @NotNull XmlTag xmlTag, final ProcessingContext context) {
        for (String attributeName : attributeNames) {
          if (xmlTag.getAttribute(attributeName) != null) {
            return true;
          }
        }
        return false;
      }
    });
  }

  public Self withDescriptor(final @NotNull ElementPattern<? extends PsiMetaData> metaDataPattern) {
    return with(new PatternCondition<>("withDescriptor") {
      @Override
      public boolean accepts(final @NotNull XmlTag xmlTag, final ProcessingContext context) {
        return metaDataPattern.accepts(xmlTag.getDescriptor());
      }
    });
  }

  public Self isFirstSubtag(final @NotNull ElementPattern pattern) {
    return with(new PatternCondition<>("isFirstSubtag") {
      @Override
      public boolean accepts(final @NotNull XmlTag xmlTag, final ProcessingContext context) {
        final XmlTag parent = xmlTag.getParentTag();
        return parent != null &&
               pattern.accepts(parent, context) && parent.getSubTags()[0] == xmlTag;
      }
    });
  }

  public Self withFirstSubTag(final @NotNull ElementPattern<? extends XmlTag> pattern) {
    return withSubTags(StandardPatterns.<XmlTag>collection().first(pattern));
  }

  public Self withSubTags(final @NotNull ElementPattern<? extends Collection<XmlTag>> pattern) {
    return with(new PatternCondition<>("withSubTags") {
      @Override
      public boolean accepts(final @NotNull XmlTag xmlTag, final ProcessingContext context) {
        return pattern.accepts(Arrays.asList(xmlTag.getSubTags()), context);
      }
    });
  }

  public Self withoutAttributeValue(final @NotNull @NonNls String attributeName, final @NotNull String attributeValue) {
    return and(StandardPatterns.not(withAttributeValue(attributeName, attributeValue)));
  }

  public static class Capture extends XmlTagPattern<Capture> {
    static final Capture XML_TAG_PATTERN = new Capture();
  }
}
