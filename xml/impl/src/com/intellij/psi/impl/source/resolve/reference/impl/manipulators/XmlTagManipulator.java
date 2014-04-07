/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.impl.source.resolve.reference.impl.manipulators;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.AbstractElementManipulator;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagValue;
import com.intellij.psi.xml.XmlText;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author Maxim.Mossienko
 */
public class XmlTagManipulator extends AbstractElementManipulator<XmlTag> {

  @Override
  public XmlTag handleContentChange(@NotNull XmlTag tag, @NotNull TextRange range, String newContent) throws IncorrectOperationException {
    
    final StringBuilder replacement = new StringBuilder( tag.getValue().getText() );
    final int valueOffset = tag.getValue().getTextRange().getStartOffset() - tag.getTextOffset();

    replacement.replace(
      range.getStartOffset() - valueOffset,
      range.getEndOffset() - valueOffset,
      newContent
    );
    tag.getValue().setEscapedText(replacement.toString());
    return tag;
  }

  @Override
  @NotNull
  public TextRange getRangeInElement(@NotNull final XmlTag tag) {
    if (tag.getSubTags().length > 0) {
      // Text range in tag with subtags is not supported, return empty range, consider making this function nullable.
      return TextRange.EMPTY_RANGE;
    }

    final XmlTagValue value = tag.getValue();
    final XmlText[] texts = value.getTextElements();
    switch (texts.length) {
      case 0:
        return value.getTextRange().shiftRight(-tag.getTextOffset());
      case 1:
        return getValueRange(texts[0]);
      default:
        return TextRange.EMPTY_RANGE;
    }
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

  public static TextRange[] getValueRanges(@NotNull final XmlTag tag) {
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
