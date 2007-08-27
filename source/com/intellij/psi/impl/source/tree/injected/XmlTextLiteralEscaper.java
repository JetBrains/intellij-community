package com.intellij.psi.impl.source.tree.injected;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.impl.source.xml.XmlTextImpl;
import com.intellij.psi.LiteralTextEscaper;
import org.jetbrains.annotations.NotNull;

/**
 * @author cdr
*/
public class XmlTextLiteralEscaper extends LiteralTextEscaper<XmlTextImpl> {
  public XmlTextLiteralEscaper(final XmlTextImpl xmlText) {
    super(xmlText);
  }

  public boolean decode(@NotNull final TextRange rangeInsideHost, @NotNull StringBuilder outChars) {
    int startInDecoded = myHost.physicalToDisplay(rangeInsideHost.getStartOffset());
    int endInDecoded = myHost.physicalToDisplay(rangeInsideHost.getEndOffset());
    outChars.append(myHost.getValue(), startInDecoded, endInDecoded);
    return true;
  }

  public int getOffsetInHost(final int offsetInDecoded, @NotNull final TextRange rangeInsideHost) {
    int displayStart = myHost.physicalToDisplay(rangeInsideHost.getStartOffset());

    int i = myHost.displayToPhysical(offsetInDecoded + displayStart);
    if (i < rangeInsideHost.getStartOffset()) i = rangeInsideHost.getStartOffset();
    if (i > rangeInsideHost.getEndOffset()) i = rangeInsideHost.getEndOffset();
    return i;
  }

  @NotNull
  public TextRange getRelevantTextRange() {
    return myHost.getCDATAInterior();
  }

  public boolean isOneLine() {
    return false;
  }
}
