package com.intellij.psi.impl.source.tree.injected;

import com.intellij.psi.impl.source.tree.java.PsiLiteralExpressionImpl;
import com.intellij.openapi.util.TextRange;

/**
 * @author cdr
*/
public class StringLiteralEscaper implements LiteralTextEscaper<PsiLiteralExpressionImpl> {
  private int[] outSourceOffsets;
  private int rangeDisplayStart;

  public boolean decode(PsiLiteralExpressionImpl host, final TextRange rangeInsideHost, StringBuilder outChars) {
    String hostText = host.getText();
    outSourceOffsets = new int[host.getTextLength()+1];
    int originalLength = outChars.length();
    PsiLiteralExpressionImpl.parseStringCharacters(hostText, outChars, outSourceOffsets);
    String subText = hostText.substring(rangeInsideHost.getStartOffset(), rangeInsideHost.getEndOffset());
    outChars.setLength(originalLength);
    StringBuilder displayCharsInTheStart = new StringBuilder();
    PsiLiteralExpressionImpl.parseStringCharacters(hostText.substring(0, rangeInsideHost.getStartOffset()),displayCharsInTheStart, null);
    rangeDisplayStart = displayCharsInTheStart.length();

    return PsiLiteralExpressionImpl.parseStringCharacters(subText, outChars, null);
  }

  public int getOffsetInHost(int offsetInDecoded, final TextRange rangeInsideHost) {
    return outSourceOffsets[offsetInDecoded + rangeDisplayStart];
  }
}
