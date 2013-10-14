package de.plushnikov.intellij.lombok.psi;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.light.LightIdentifier;

/**
 * Date: 12.10.13 Time: 23:27
 */
public class LombokLightIdentifier extends LightIdentifier {
  public LombokLightIdentifier(PsiManager manager, String text) {
    super(manager, text);
  }

  @Override
  public TextRange getTextRange() {
    TextRange r = super.getTextRange();
    return r == null ? TextRange.EMPTY_RANGE : r;
  }
}
