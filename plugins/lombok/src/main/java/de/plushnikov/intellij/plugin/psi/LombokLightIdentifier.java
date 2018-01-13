package de.plushnikov.intellij.plugin.psi;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.light.LightIdentifier;

/**
 * Date: 12.10.13 Time: 23:27
 */
public class LombokLightIdentifier extends LightIdentifier {
  private String myText;

  public LombokLightIdentifier(PsiManager manager, String text) {
    super(manager, text);
    myText = text;
  }

  @Override
  public String getText() {
    return myText;
  }

  public void setText(String text) {
    myText = text;
  }

  @Override
  public PsiElement copy() {
    return new LightIdentifier(getManager(), getText());
  }

  @Override
  public TextRange getTextRange() {
    TextRange r = super.getTextRange();
    return r == null ? TextRange.EMPTY_RANGE : r;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    LombokLightIdentifier that = (LombokLightIdentifier) o;

    return !(myText != null ? !myText.equals(that.myText) : that.myText != null);

  }

  @Override
  public int hashCode() {
    return myText != null ? myText.hashCode() : 0;
  }
}
