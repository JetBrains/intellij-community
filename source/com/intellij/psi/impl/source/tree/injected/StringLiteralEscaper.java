package com.intellij.psi.impl.source.tree.injected;

import com.intellij.psi.impl.source.tree.java.PsiLiteralExpressionImpl;
import com.intellij.psi.LiteralTextEscaper;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.ProperTextRange;
import org.jetbrains.annotations.NotNull;

/**
 * @author cdr
*/
public class StringLiteralEscaper extends LiteralTextEscaper<PsiLiteralExpressionImpl> {
  private int[] outSourceOffsets;

  public StringLiteralEscaper(PsiLiteralExpressionImpl host) {
    super(host);
  }

  public boolean decode(@NotNull final TextRange rangeInsideHost, @NotNull StringBuilder outChars) {
    ProperTextRange.assertProperRange(rangeInsideHost);
    String subText = rangeInsideHost.substring(myHost.getText());
    outSourceOffsets = new int[subText.length()+1];
    return PsiLiteralExpressionImpl.parseStringCharacters(subText, outChars, outSourceOffsets);
  }

  public int getOffsetInHost(int offsetInDecoded, @NotNull final TextRange rangeInsideHost) {
    int result = offsetInDecoded < outSourceOffsets.length ? outSourceOffsets[offsetInDecoded] : -1;
    if (result == -1) return -1;
    return (result <= rangeInsideHost.getLength() ? result : rangeInsideHost.getLength()) + rangeInsideHost.getStartOffset();
  }

  public boolean isOneLine() {
    return true;
  }
}
