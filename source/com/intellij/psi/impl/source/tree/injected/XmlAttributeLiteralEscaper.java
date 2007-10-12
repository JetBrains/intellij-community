package com.intellij.psi.impl.source.tree.injected;

import com.intellij.psi.impl.source.xml.XmlAttributeValueImpl;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.LiteralTextEscaper;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;

/**
 * @author cdr
*/
public class XmlAttributeLiteralEscaper extends LiteralTextEscaper<XmlAttributeValueImpl> {
  private final XmlAttribute myXmlAttribute;

  public XmlAttributeLiteralEscaper(XmlAttributeValueImpl host) {
    super(host);
    myXmlAttribute = (XmlAttribute)host.getParent();
  }

  public boolean decode(@NotNull final TextRange rangeInsideHost, @NotNull StringBuilder outChars) {
    TextRange valueTextRange = myXmlAttribute.getValueTextRange();
    int startInDecoded = myXmlAttribute.physicalToDisplay(rangeInsideHost.getStartOffset() - valueTextRange.getStartOffset());
    int endInDecoded = myXmlAttribute.physicalToDisplay(rangeInsideHost.getEndOffset() - valueTextRange.getStartOffset());
    String displayValue = myXmlAttribute.getDisplayValue();
    //todo investigate IIOB http://www.jetbrains.net/jira/browse/IDEADEV-16796
    startInDecoded = startInDecoded < 0 ? 0 : startInDecoded > displayValue.length() ? displayValue.length() : startInDecoded;
    endInDecoded = endInDecoded < 0 ? 0 : endInDecoded > displayValue.length() ? displayValue.length() : endInDecoded;
    if (startInDecoded > endInDecoded) endInDecoded = startInDecoded;
    outChars.append(displayValue, startInDecoded, endInDecoded);
    return true;
  }

  public int getOffsetInHost(final int offsetInDecoded, @NotNull final TextRange rangeInsideHost) {
    TextRange valueTextRange = myXmlAttribute.getValueTextRange();
    int displayStart = myXmlAttribute.physicalToDisplay(rangeInsideHost.getStartOffset());

    int dp = myXmlAttribute.displayToPhysical(offsetInDecoded + displayStart - valueTextRange.getStartOffset());
    if (dp == -1) return -1;
    return dp + valueTextRange.getStartOffset();
  }

  public boolean isOneLine() {
    return true;
  }
}
