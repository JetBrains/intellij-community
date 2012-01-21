package de.plushnikov.intellij.lombok.psi;

import com.intellij.lang.Language;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.light.LightIdentifier;
import com.intellij.psi.impl.light.LightParameter;
import org.jetbrains.annotations.NotNull;

/**
 * @author Plushnikov Michail
 */
public class LombokLightParameter10Impl extends LightParameter {
  private final LightIdentifier myNameIdentifier;

  public LombokLightParameter10Impl(@NotNull String name, @NotNull PsiType type, PsiElement declarationScope, Language language) {
    super(name, type, declarationScope, language);
    myNameIdentifier = new LightIdentifier(declarationScope.getManager(), name);
  }

  @Override
  public PsiIdentifier getNameIdentifier() {
    return myNameIdentifier;
  }
}
