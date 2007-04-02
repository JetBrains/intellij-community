package com.intellij.psi.impl.source.tree.injected;

import com.intellij.psi.impl.source.xml.XmlTextImpl;
import com.intellij.psi.xml.XmlText;
import com.intellij.openapi.util.TextRange;

/**
 * @author cdr
*/
public class XmlTextLiteralEscaper implements LiteralTextEscaper<XmlTextImpl> {
  private final XmlText myXmlText;

  public XmlTextLiteralEscaper(final XmlText xmlText) {
    myXmlText = xmlText;
  }

  public boolean decode(XmlTextImpl host, final TextRange rangeInsideHost, StringBuilder outChars) {
    int startInDecoded = host.physicalToDisplay(rangeInsideHost.getStartOffset());
    int endInDecoded = host.physicalToDisplay(rangeInsideHost.getEndOffset());
    outChars.append(host.getValue(), startInDecoded, endInDecoded);
    return true;
  }

  public int getOffsetInHost(final int offsetInDecoded, final TextRange rangeInsideHost) {
    int displayStart = myXmlText.physicalToDisplay(rangeInsideHost.getStartOffset());

    return myXmlText.displayToPhysical(offsetInDecoded+displayStart);
    
    //return myXmlText.displayToPhysical(offsetInDecoded);
  }
}
