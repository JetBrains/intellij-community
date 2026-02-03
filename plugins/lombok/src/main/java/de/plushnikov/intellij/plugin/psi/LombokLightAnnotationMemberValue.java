package de.plushnikov.intellij.plugin.psi;

import com.intellij.lang.Language;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiManager;
import com.intellij.psi.SyntheticElement;
import com.intellij.psi.impl.light.LightElement;
import org.jetbrains.annotations.NotNull;

public class LombokLightAnnotationMemberValue extends LightElement implements PsiAnnotationMemberValue, SyntheticElement {

  public LombokLightAnnotationMemberValue(@NotNull PsiManager manager,
                                          @NotNull Language language) {
    super(manager, language);
  }

  @Override
  public String toString() {
    return "LombokLightAnnotationMemberValue:" + getName();
  }
}
