package de.plushnikov.intellij.plugin.psi;

import com.intellij.lang.Language;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.light.LightReferenceListBuilder;

public class LombokLightReferenceListBuilder extends LightReferenceListBuilder {

  public LombokLightReferenceListBuilder(PsiManager manager, Language language, Role role) {
    super(manager, language, role);
  }

  @Override
  public TextRange getTextRange() {
    TextRange r = super.getTextRange();
    return r == null ? TextRange.EMPTY_RANGE : r;
  }
}
