package com.intellij.patterns;

import com.intellij.openapi.util.Comparing;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;

/**
 * @author spleaner
*/
public class XmlTagPattern<Self extends XmlTagPattern<Self>> extends XmlNamedElementPattern<XmlTag, Self> {
  protected XmlTagPattern() {
    super(new InitialPatternCondition<XmlTag>(XmlTag.class) {
      public boolean accepts(@Nullable final Object o,
                                final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
        return o instanceof XmlTag;
      }
    });
  }

  protected XmlTagPattern(@NotNull final InitialPatternCondition<XmlTag> condition) {
    super(condition);
  }

  protected String getLocalName(XmlTag tag) {
    return tag.getLocalName();
  }

  protected String getNamespace(XmlTag tag) {
    return tag.getNamespace();
  }

  public Self withAttributeValue(@NotNull @NonNls final String attributeName, @NotNull final String attributeValue) {
    return with(new PatternCondition<XmlTag>("withAttributeValue") {
      public boolean accepts(@NotNull final XmlTag xmlTag,
                                final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
        return Comparing.equal(xmlTag.getAttributeValue(attributeName), attributeValue);
      }
    });
  }

  public Self isFirstSubtag(@NotNull final ElementPattern pattern) {
    return with(new PatternCondition<XmlTag>("isFirstSubtag") {
      public boolean accepts(@NotNull final XmlTag xmlTag,
                                final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
        final XmlTag parent = xmlTag.getParentTag();
        return parent != null &&
               pattern.getCondition().accepts(parent, matchingContext, traverseContext) && parent.getSubTags()[0] == xmlTag;
      }
    });
  }

  public Self withFirstSubTag(@NotNull final ElementPattern<? extends XmlTag> pattern) {
    return withSubTags(StandardPatterns.<XmlTag>collection().first(pattern));
  }

  public Self withSubTags(@NotNull final ElementPattern<? extends Collection<XmlTag>> pattern) {
    return with(new PatternCondition<XmlTag>("withSubTags") {
      public boolean accepts(@NotNull final XmlTag xmlTag,
                                final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
        return pattern.getCondition().accepts(Arrays.asList(xmlTag.getSubTags()), matchingContext, traverseContext);
      }
    });
  }

  public Self withoutAttributeValue(@NotNull @NonNls final String attributeName, @NotNull final String attributeValue) {
    return and(StandardPatterns.not(withAttributeValue(attributeName, attributeValue)));
  }

  public static class Capture extends XmlTagPattern<Capture> {
  }
}
