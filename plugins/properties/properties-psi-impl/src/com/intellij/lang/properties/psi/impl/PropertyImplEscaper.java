package com.intellij.lang.properties.psi.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.LiteralTextEscaper;
import org.jetbrains.annotations.NotNull;

/**
 * @author gregsh
 */
class PropertyImplEscaper extends LiteralTextEscaper<PropertyImpl> {
  private static final Logger LOG = Logger.getInstance(PropertyImplEscaper.class);

  private int[] outSourceOffsets;

  PropertyImplEscaper(@NotNull PropertyImpl value) {
    super(value);
  }

  @Override
  public boolean decode(@NotNull TextRange rangeInsideHost, @NotNull StringBuilder outChars) {
    String subText = rangeInsideHost.substring(myHost.getText());
    outSourceOffsets = new int[subText.length() + 1];
    int prefixLen = outChars.length();
    boolean b = PropertyImpl.parseCharacters(subText, outChars, outSourceOffsets);
    if (b) {
      for (int i = prefixLen, len = outChars.length(); i < len; i++) {
        char outChar = outChars.charAt(i);
        char inChar = subText.charAt(outSourceOffsets[i - prefixLen]);
        if (outChar != inChar && inChar != '\\') {
          LOG.error("input: " + subText + ";\noutput: " + outChars +
                    "\nat: " + i + "; prefix-length: " + prefixLen);
        }
      }
    }
    return b;
  }

  @Override
  public int getOffsetInHost(int offsetInDecoded, @NotNull TextRange rangeInsideHost) {
    int result = offsetInDecoded < outSourceOffsets.length ? outSourceOffsets[offsetInDecoded] : -1;
    if (result == -1) return -1;
    return (result <= rangeInsideHost.getLength() ? result : rangeInsideHost.getLength()) + rangeInsideHost.getStartOffset();
  }

  @Override
  public boolean isOneLine() {
    return !myHost.getText().contains("\\");
  }
}
