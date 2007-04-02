package com.intellij.psi.impl.source.tree.injected;

import com.intellij.psi.impl.source.xml.XmlAttributeValueImpl;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.openapi.util.TextRange;

/**
 * @author cdr
*/
public class XmlAttributeLiteralEscaper implements LiteralTextEscaper<XmlAttributeValueImpl> {
  private final XmlAttribute myXmlAttribute;

  public XmlAttributeLiteralEscaper(final XmlAttribute xmlAttribute) {
    myXmlAttribute = xmlAttribute;
  }

  public boolean decode(XmlAttributeValueImpl host, final TextRange rangeInsideHost, StringBuilder outChars) {
    TextRange valueTextRange = myXmlAttribute.getValueTextRange();
    int startInDecoded = myXmlAttribute.physicalToDisplay(rangeInsideHost.getStartOffset() - valueTextRange.getStartOffset());
    int endInDecoded = myXmlAttribute.physicalToDisplay(rangeInsideHost.getEndOffset() - valueTextRange.getStartOffset());
    outChars.append(myXmlAttribute.getDisplayValue(), startInDecoded, endInDecoded);
    return true;
  }

  public int getOffsetInHost(final int offsetInDecoded, final TextRange rangeInsideHost) {
    TextRange valueTextRange = myXmlAttribute.getValueTextRange();
    int displayStart = myXmlAttribute.physicalToDisplay(rangeInsideHost.getStartOffset());

    return myXmlAttribute.displayToPhysical(offsetInDecoded+displayStart-valueTextRange.getStartOffset()) 
           + valueTextRange.getStartOffset();
  }
}
