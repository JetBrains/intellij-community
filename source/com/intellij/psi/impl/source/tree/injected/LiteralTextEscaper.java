package com.intellij.psi.impl.source.tree.injected;

import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.openapi.util.TextRange;

/**
 * @author cdr
*/
public interface LiteralTextEscaper<T extends PsiLanguageInjectionHost> {
  boolean decode(T host, final TextRange rangeInsideHost, StringBuilder outChars);

  /**
   * 
   * @param offsetInDecoded offset in the parsed injected file
   * @param rangeInsideHost
   * @return offset in the host PSI element
   */
  int getOffsetInHost(int offsetInDecoded, final TextRange rangeInsideHost);
}
