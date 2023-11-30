// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.resolve.reference.impl.manipulators;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagValue;
import com.intellij.psi.xml.XmlText;
import org.jetbrains.annotations.NotNull;

/**
 * @author Maxim.Mossienko
 */
public class XmlTagManipulator extends SimpleTagManipulator<XmlTag> {
  @Override
  public @NotNull TextRange getRangeInElement(final @NotNull XmlTag tag) {
    if (tag.getSubTags().length > 0) {
      // Text range in tag with subtags is not supported, return empty range, consider making this function nullable.
      return TextRange.EMPTY_RANGE;
    }

    final XmlTagValue value = tag.getValue();
    final XmlText[] texts = value.getTextElements();
    return switch (texts.length) {
      case 0 -> value.getTextRange().shiftRight(-tag.getTextOffset());
      case 1 -> getValueRange(texts[0]);
      default -> TextRange.EMPTY_RANGE;
    };
  }

  private static TextRange getValueRange(final XmlText xmlText) {
    final int offset = xmlText.getStartOffsetInParent();
    final String value = xmlText.getValue();
    final String trimmed = value.trim();
    final int i = value.indexOf(trimmed);
    final int start = xmlText.displayToPhysical(i) + offset;    
    return trimmed.isEmpty()
           ? new TextRange(start, start) : new TextRange(start, xmlText.displayToPhysical(i + trimmed.length() - 1) + offset + 1);
  }

  public static TextRange[] getValueRanges(final @NotNull XmlTag tag) {
    final XmlTagValue value = tag.getValue();
    final XmlText[] texts = value.getTextElements();
    if (texts.length == 0) {
      return new TextRange[] { value.getTextRange().shiftRight(-tag.getTextOffset()) };
    } else {
      final TextRange[] ranges = new TextRange[texts.length];
      for (int i = 0; i < texts.length; i++) {
        ranges[i] = getValueRange(texts[i]);
      }
      return ranges;
    }
  }
}
