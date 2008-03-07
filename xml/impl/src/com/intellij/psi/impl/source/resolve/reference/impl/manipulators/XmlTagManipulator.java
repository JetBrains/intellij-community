package com.intellij.psi.impl.source.resolve.reference.impl.manipulators;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.AbstractElementManipulator;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagValue;
import com.intellij.util.IncorrectOperationException;

/**
 * @author Maxim.Mossienko
 */
public class XmlTagManipulator extends AbstractElementManipulator<XmlTag> {

  public XmlTag handleContentChange(XmlTag tag, TextRange range, String newContent) throws IncorrectOperationException {
    
    final StringBuilder replacement = new StringBuilder( tag.getValue().getText() );
    final int valueOffset = tag.getValue().getTextRange().getStartOffset() - tag.getTextOffset();

    replacement.replace(
      range.getStartOffset() - valueOffset,
      range.getEndOffset() - valueOffset,
      newContent
    );
    tag.getValue().setText(replacement.toString());
    return tag;
  }

  public TextRange getRangeInElement(final XmlTag tag) {
    if (tag.getSubTags().length > 0) {
      // Text range in tag with subtags is not supported, return empty range, consider making this function nullable.
      return new TextRange(0, 0);
    }

    final XmlTagValue value = tag.getValue();
    final String text = value.getText();
    final String trimmedText = value.getTrimmedText();
    final int index = text.indexOf(trimmedText);
    int valueStart = value.getTextRange().getStartOffset() + index - tag.getTextOffset();
    return new TextRange(valueStart, valueStart + trimmedText.length());
  }
}
