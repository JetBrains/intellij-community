// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.patterns;

import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ProcessingContext;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author spleaner
*/
public class XmlTagPattern<Self extends XmlTagPattern<Self>> extends XmlNamedElementPattern<XmlTag, Self> {
  protected XmlTagPattern() {
    super(new InitialPatternCondition<>(XmlTag.class) {
      @Override
      public boolean accepts(@Nullable final Object o, final ProcessingContext context) {
        return o instanceof XmlTag;
      }
    });
  }

  protected XmlTagPattern(@NotNull final InitialPatternCondition<XmlTag> condition) {
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

  public Self withAttributeValue(@NotNull @NonNls final String attributeName, @NotNull final String attributeValue) {
    return with(new PatternCondition<>("withAttributeValue") {
      @Override
      public boolean accepts(@NotNull final XmlTag xmlTag, final ProcessingContext context) {
        return Objects.equals(xmlTag.getAttributeValue(attributeName), attributeValue);
      }
    });
  }

  public Self withAnyAttribute(@NonNls final String @NotNull ... attributeNames) {
    return with(new PatternCondition<>("withAnyAttribute") {
      @Override
      public boolean accepts(@NotNull final XmlTag xmlTag, final ProcessingContext context) {
        for (String attributeName : attributeNames) {
          if (xmlTag.getAttribute(attributeName) != null) {
            return true;
          }
        }
        return false;
      }
    });
  }

  public Self withDescriptor(@NotNull final ElementPattern<? extends PsiMetaData> metaDataPattern) {
    return with(new PatternCondition<>("withDescriptor") {
      @Override
      public boolean accepts(@NotNull final XmlTag xmlTag, final ProcessingContext context) {
        return metaDataPattern.accepts(xmlTag.getDescriptor());
      }
    });
  }

  public Self isFirstSubtag(@NotNull final ElementPattern pattern) {
    return with(new PatternCondition<>("isFirstSubtag") {
      @Override
      public boolean accepts(@NotNull final XmlTag xmlTag, final ProcessingContext context) {
        final XmlTag parent = xmlTag.getParentTag();
        return parent != null &&
               pattern.accepts(parent, context) && parent.getSubTags()[0] == xmlTag;
      }
    });
  }

  public Self withFirstSubTag(@NotNull final ElementPattern<? extends XmlTag> pattern) {
    return withSubTags(StandardPatterns.<XmlTag>collection().first(pattern));
  }

  public Self withSubTags(@NotNull final ElementPattern<? extends Collection<XmlTag>> pattern) {
    return with(new PatternCondition<>("withSubTags") {
      @Override
      public boolean accepts(@NotNull final XmlTag xmlTag, final ProcessingContext context) {
        return pattern.accepts(Arrays.asList(xmlTag.getSubTags()), context);
      }
    });
  }

  public Self withoutAttributeValue(@NotNull @NonNls final String attributeName, @NotNull final String attributeValue) {
    return and(StandardPatterns.not(withAttributeValue(attributeName, attributeValue)));
  }

  public static class Capture extends XmlTagPattern<Capture> {
    static final Capture XML_TAG_PATTERN = new Capture();
  }
}
