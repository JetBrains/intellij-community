package com.intellij.psi.impl.source.xml;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.xml.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class XmlTagValueImpl implements XmlTagValue{
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.xml.XmlTagValueImpl");

  private final XmlTag myTag;
  private final XmlTagChild[] myElements;
  private XmlText[] myTextElements = null;
  private String myText = null;
  private String myTrimmedText = null;

  public XmlTagValueImpl(@NotNull XmlTagChild[] bodyElements, @NotNull XmlTag tag) {
    myTag = tag;
    myElements = bodyElements;
  }

  @NotNull
  public XmlTagChild[] getChildren() {
    return myElements;
  }

  @NotNull
  public XmlText[] getTextElements() {
    if(myTextElements != null) return myTextElements;
    final List<XmlText> textElements = new ArrayList<XmlText>();
    for (final XmlTagChild element : myElements) {
      if (element instanceof XmlText) textElements.add((XmlText)element);
    }
    return myTextElements = textElements.toArray(new XmlText[textElements.size()]);
  }

  @NotNull
  public String getText() {
    if(myText != null) return myText;
    final StringBuffer consolidatedText = new StringBuffer();
    for (final XmlTagChild element : myElements) {
      consolidatedText.append(element.getText());
    }
    return consolidatedText.toString();
  }

  @NotNull
  public TextRange getTextRange() {
    if(myElements.length == 0){
      final ASTNode child = XmlChildRole.START_TAG_END_FINDER.findChild( (ASTNode)myTag);
      if(child != null)
        return new TextRange(child.getStartOffset() + 1, child.getStartOffset() + 1);
      return new TextRange(myTag.getTextRange().getEndOffset(), myTag.getTextRange().getEndOffset());
    }
    return new TextRange(myElements[0].getTextRange().getStartOffset(), myElements[myElements.length - 1].getTextRange().getEndOffset());
  }

  @NotNull
  public String getTrimmedText() {
    if(myTrimmedText != null) return myTrimmedText;

    final StringBuffer consolidatedText = new StringBuffer();
    final XmlText[] textElements = getTextElements();
    for (final XmlText textElement : textElements) {
      consolidatedText.append(textElement.getValue());
    }
    return myTrimmedText = consolidatedText.toString().trim();
  }

  public void setText(String value) {
    try {
      if(myElements.length > 0){
        myTag.deleteChildRange(myElements[0], myElements[myElements.length - 1]);
      }
      if(value != null && value.length() > 0) {
        XmlText displayText = myTag.getManager().getElementFactory().createDisplayText("x");
        displayText = (XmlText)myTag.add(displayText);
        displayText.setValue(value);
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }
}
