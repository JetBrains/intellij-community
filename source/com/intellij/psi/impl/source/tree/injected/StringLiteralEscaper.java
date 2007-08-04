package com.intellij.psi.impl.source.tree.injected;

import com.intellij.psi.impl.source.tree.java.PsiLiteralExpressionImpl;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;

/**
 * @author cdr
*/
public class StringLiteralEscaper extends LiteralTextEscaper<PsiLiteralExpressionImpl> {
  private int[] outSourceOffsets;
  private int rangeDisplayStart;

  public StringLiteralEscaper(PsiLiteralExpressionImpl host) {
    super(host);
  }

  public boolean decode(@NotNull final TextRange rangeInsideHost, @NotNull StringBuilder outChars) {
    String hostText = myHost.getText();
    outSourceOffsets = new int[myHost.getTextLength()+1];
    int originalLength = outChars.length();
    PsiLiteralExpressionImpl.parseStringCharacters(hostText, outChars, outSourceOffsets);
    String subText = hostText.substring(rangeInsideHost.getStartOffset(), rangeInsideHost.getEndOffset());
    outChars.setLength(originalLength);
    StringBuilder displayCharsInTheStart = new StringBuilder();
    PsiLiteralExpressionImpl.parseStringCharacters(hostText.substring(0, rangeInsideHost.getStartOffset()),displayCharsInTheStart, null);
    rangeDisplayStart = displayCharsInTheStart.length();

    return PsiLiteralExpressionImpl.parseStringCharacters(subText, outChars, null);
  }

  public int getOffsetInHost(int offsetInDecoded, @NotNull final TextRange rangeInsideHost) {
    return outSourceOffsets[offsetInDecoded + rangeDisplayStart];
  }
}
